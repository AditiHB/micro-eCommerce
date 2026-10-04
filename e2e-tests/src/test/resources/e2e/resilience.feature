Feature: Gateway resilience patterns
  Exercises the gateway's circuit breaker/fallback and rate limiter against
  the live stack.

  Both of these were actually NON-FUNCTIONAL for 4 of 5 routes until a real
  bug was found and fixed while building this feature: infrastructure/
  api-gateway's GatewayConfiguration used to define a second, programmatic
  RouteLocator bean whose routes for order/customer/inventory/payment-service
  shared the exact same route IDs as the properly-filtered ones in
  application.yml but had NO filters attached at all (no CircuitBreaker, no
  RateLimitingFilter). Spring Cloud Gateway does not deduplicate routes by ID
  across different RouteLocator sources, and the filter-less duplicates were
  winning the match for those 4 routes - silently bypassing the circuit
  breaker and rate limiter while requests still worked normally (each
  backend independently validates its own JWT regardless of what the gateway
  does, so a missing/bad token still correctly 401'd and masked the gap).
  Confirmed via /actuator/circuitbreakers (bufferedCalls stuck at 0 for every
  route except auth-service) and redis-cli MONITOR (rate limit key never
  incremented past 1 under load) before the fix; both now work correctly -
  see GatewayConfiguration.java's history for the full writeup.

  Separately: RateLimitingFilter's own read-then-write (GET then a separate
  INCR) was also fixed to a single atomic INCR, since the old version could
  under-count a genuine burst of concurrent requests arriving between the
  read and the write.

  Bulkhead is NOT covered here - it isn't implemented anywhere in this app
  (no Bulkhead annotation, no bulkhead config, anywhere in the repo).
  order-service's OWN circuit breaker/retry (a direct AOP proxy around
  createOrder's method body, separate from the gateway's) is also not
  covered - it can only be tripped by making Kafka itself fail, and Kafka is
  a shared dependency for 4 services with a documented history of flaky
  restarts in this project, too large a blast radius for this feature.

  Scenario order matters: the circuit-breaker scenario runs FIRST, with the
  rate-limit scenario's heavy traffic LAST - the gateway's rate limiter
  counter is keyed only by client IP and shared across every route, so
  exhausting it early would make the circuit-breaker scenario's own payment
  calls get 429'd before they ever reach the backend.

  Background:
    * url gatewayUrl
    * def showcase = Java.type('e2e.DataShowcase')
    Given path '/api/auth/login'
    And request { username: '#(testUsername)', password: '#(testPassword)' }
    When method post
    Then status 200
    * def authToken = response.token
    * configure headers = { Authorization: '#("Bearer " + authToken)' }

  Scenario: Gateway circuit breaker opens when payment-service is unreachable, then recovers
    * def docker = Java.type('e2e.DockerControl')
    # Guaranteed to run even if an assertion below fails.
    * configure afterScenario = function(){ docker.start('payment-service') }
    * showcase.event('Stopping payment-service to force real connection failures through the gateway - not a mock, a genuinely unreachable backend.')
    * docker.stop('payment-service')

    # A fresh, unique orderId - "one payment per order" is now an atomic DB
    # constraint (V7__Enforce_One_Payment_Per_Order.sql), and order 1
    # already has a seeded payment from the start, so a hardcoded orderId
    # would make the recovery check below get a permanent 400
    # PAYMENT_ALREADY_EXISTS instead of ever reaching 201.
    * def orderId = Java.type('java.lang.System').currentTimeMillis()

    # minimumNumberOfCalls=5 / slidingWindowSize=10 / failureRateThreshold=50%
    # (see application.yml's resilience4j.circuitbreaker.configs.default) -
    # a handful of failed calls is enough to open it; each failing call
    # takes a few seconds on its own (real connection attempt/timeout) until
    # it does.
    * configure retry = { count: 10, interval: 1000 }
    Given path '/api/payments'
    And request { orderId: '#(orderId)', amount: 10.00 }
    And retry until responseStatus == 503 && response.circuitBreakerStatus == 'OPEN'
    When method post
    Then status 503
    And match response.circuitBreakerStatus == 'OPEN'
    And match response.error == 'Service Unavailable'
    * showcase.event('Circuit breaker OPEN after enough failed calls - the gateway is now fast-failing every request instead of waiting on a dead backend each time.')
    * showcase.show('Payments for order ' + orderId + ' while breaker is OPEN - expect zero rows (payment-service never reached)', 'payment_db', 'SELECT id, order_id, amount, status FROM payments WHERE order_id=' + orderId)

    # Recovery: restart the container, wait past waitDurationInOpenState
    # (30s), and confirm the breaker lets traffic through again
    # (half-open -> closed) once it's actually reachable.
    * docker.start('payment-service')
    * assert docker.waitUntilHealthy('payment-service', 60)
    * showcase.event('payment-service back up and healthy - the breaker will probe it on the next call (half-open) and close again once that succeeds.')

    * configure retry = { count: 20, interval: 3000 }
    Given path '/api/payments'
    And request { orderId: '#(orderId)', amount: 10.00 }
    And retry until responseStatus == 201
    When method post
    Then status 201
    And match response.status == 'PROCESSED'
    * showcase.event('Breaker transitioned OPEN -> HALF_OPEN -> CLOSED - real traffic flows normally again now that the backend is actually reachable.')
    * showcase.show('Payment for order ' + orderId + ' after recovery', 'payment_db', 'SELECT id, order_id, amount, status FROM payments WHERE order_id=' + orderId)

  Scenario: Gateway rate limiter returns 429 once a route's per-minute budget is exceeded
    # payment's route has the lowest budget (50/min - see
    # infrastructure/api-gateway/src/main/resources/application.yml) and the
    # counter is a sliding 60s TTL that renews on every hit, so firing enough
    # requests in a tight loop reliably exceeds it well within 60 seconds.
    #
    # This deliberately leaves the shared per-client-IP counter exhausted
    # for up to 60s afterward (every route shares one counter - see
    # RateLimitingFilter) - clean it up even if an assertion below fails,
    # so a different feature run within that window doesn't get a false
    # 429 that has nothing to do with whatever IT's testing.
    * def docker = Java.type('e2e.DockerControl')
    * configure afterScenario = function(){ docker.clearRateLimitKeys() }
    * showcase.event('Firing 60 rapid requests at the payment route (50/min budget) - this state lives in Redis, not Postgres, so there is no SQL table to show here.')

    * def probe = function(){ return karate.call('classpath:e2e/rate-limit-probe.feature', { gatewayUrl: gatewayUrl, authToken: authToken }) }
    * def statuses = []
    * eval for (var i = 0; i < 60; i++) { statuses.push(probe().responseStatus) }
    * assert statuses.includes(429)
    * showcase.event('Budget exceeded - at least one request got 429 before the loop finished, confirming the atomic INCR-based limiter actually counts every hit.')
