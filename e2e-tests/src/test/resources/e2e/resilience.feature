Feature: Resilience - what happens when Kafka or a service is down
  These scenarios stop real containers of the running stack and prove the design's promises:

  * Orders are ACCEPTED while Kafka is down, and complete by themselves when it comes back: the order and its
    event are written to the database in one transaction (the outbox); the relay delivers the event when the
    broker returns. Nothing is lost, nothing is duplicated.
  * A late cancellation is safe: an order cancelled while its saga events are stuck in the outbox ends up fully
    compensated once everything flows again.
  * A dependency outage is a clean 503 problem with a stable error code - never a 500, never a fabricated success
    body - and does not leave a half-created order behind.

  Needs `docker` on PATH (see DockerControl). Each scenario restarts whatever it stopped, even if it fails.

  Background:
    * url gatewayUrl
    * def showcase = Java.type('e2e.DataShowcase')
    * def docker = Java.type('e2e.DockerControl')
    * eval docker.clearRateLimitKeys()
    * def login = call read('classpath:e2e/auth.feature') { username: '#(testUsername)', password: '#(testPassword)' }
    * def token = login.accessToken
    * configure headers = { Authorization: '#("Bearer " + token)' }

  Scenario: Orders are accepted while Kafka is down and complete after it comes back (transactional outbox)

    * configure afterScenario = function(){ docker.start('kafka') }
    * def customer = call read('classpath:e2e/helpers/create-customer.feature') { token: '#(token)' }
    * def product = call read('classpath:e2e/helpers/create-product.feature') { token: '#(token)', price: 30.00, stock: 10 }

    * showcase.event('Stopping Kafka: the broker is genuinely gone, not mocked.')
    * eval docker.stop('kafka')

    # The API still answers: the order and its event are committed to order_db; only the relay's delivery waits.
    * def placed = call read('classpath:e2e/helpers/place-order.feature') { token: '#(token)', customerId: '#(customer.customerId)', items: [{ productId: '#(product.sku)', quantity: 2 }] }
    * def orderId = placed.orderId
    * match placed.order.status == 'PENDING'
    * showcase.show('The event waits in order-service\'s outbox while Kafka is down', 'order_db', "SELECT event_type, status, attempts FROM outbox_event WHERE aggregate_id='" + orderId + "'")

    # Nothing can happen while the broker is down...
    * eval java.lang.Thread.sleep(4000)
    Given path '/api/v1/orders', orderId
    When method get
    Then status 200
    And match response.status == 'PENDING'

    * showcase.event('Starting Kafka again.')
    * eval docker.start('kafka')
    * assert docker.waitUntilHealthy('kafka', 120)

    # ... and once it is back the saga simply runs: nothing was lost.
    * def settled = call read('classpath:e2e/helpers/await-order.feature') { token: '#(token)', orderId: '#(orderId)', expected: 'COMPLETED', attempts: 150 }
    Given path '/api/v1/payments/order', orderId
    When method get
    Then status 200
    And match response.status == 'CAPTURED'
    And match response.amount == 60.00
    * showcase.show('The event was published once the broker returned', 'order_db', "SELECT event_type, status, attempts FROM outbox_event WHERE aggregate_id='" + orderId + "' ORDER BY id")

  Scenario: An order cancelled while its events are still stuck ends fully compensated (late-event safety net)

    * configure afterScenario = function(){ docker.start('kafka') }
    * def customer = call read('classpath:e2e/helpers/create-customer.feature') { token: '#(token)' }
    * def product = call read('classpath:e2e/helpers/create-product.feature') { token: '#(token)', price: 15.00, stock: 6 }
    * eval docker.stop('kafka')

    * def placed = call read('classpath:e2e/helpers/place-order.feature') { token: '#(token)', customerId: '#(customer.customerId)', items: [{ productId: '#(product.sku)', quantity: 2 }] }
    * def orderId = placed.orderId

    # Cancel while nothing has left the building yet: the order service writes order.cancelled to the outbox too.
    Given path '/api/v1/orders', orderId, 'cancel'
    And param reason = 'Karate: cancelled while Kafka is down'
    When method post
    Then status 200
    And match response.status == 'CANCELLED'

    * eval docker.start('kafka')
    * assert docker.waitUntilHealthy('kafka', 120)

    # Order created and cancelled now race through the services. Whatever order they are handled in, the end state is
    # the same: order CANCELLED, stock back at 6, and nothing left charged.
    * configure retry = { count: 90, interval: 1000 }
    Given path '/api/v1/inventory', product.inventoryId
    And retry until response.quantity == 6
    When method get
    Then status 200

    Given path '/api/v1/orders', orderId
    When method get
    Then status 200
    And match response.status == 'CANCELLED'

    # payment: either never charged, or charged and then refunded - never left CAPTURED
    * configure retry = { count: 60, interval: 1000 }
    Given path '/api/v1/payments/order', orderId
    And retry until responseStatus == 404 || response.status == 'REFUNDED' || response.status == 'FAILED'
    When method get
    * assert responseStatus == 404 || response.status == 'REFUNDED' || response.status == 'FAILED'
    * showcase.event('Compensated: order CANCELLED, stock restored to 6, payment ' + (responseStatus == 404 ? 'never taken' : response.status) + '.')

  Scenario: A dependency outage is a clean 503 problem and leaves no order behind

    * configure afterScenario = function(){ docker.start('product-service') }
    * def customer = call read('classpath:e2e/helpers/create-customer.feature') { token: '#(token)' }
    * def product = call read('classpath:e2e/helpers/create-product.feature') { token: '#(token)', price: 10.00, stock: 5 }
    * eval docker.stop('product-service')

    # order-service cannot price the order without the catalogue: 503, with a stable code, and retryable
    Given path '/api/v1/orders'
    And request { customerId: '#(customer.customerId)', items: [{ productId: '#(product.sku)', quantity: 1 }] }
    When method post
    Then status 503
    And match response.errorCode == 'DEPENDENCY_UNAVAILABLE'
    And match responseHeaders['Content-Type'][0] contains 'application/problem+json'

    * eval docker.start('product-service')
    * assert docker.waitUntilHealthy('product-service', 120)
    * def after = call read('classpath:e2e/helpers/count-orders.feature') { token: '#(token)', customerId: '#(customer.customerId)' }
    * match after.total == 0

    # once the dependency is back the very same request works
    * configure retry = { count: 30, interval: 1000 }
    Given path '/api/v1/orders'
    And request { customerId: '#(customer.customerId)', items: [{ productId: '#(product.sku)', quantity: 1 }] }
    And retry until responseStatus == 201
    When method post
    Then status 201

  Scenario: The gateway answers 503 (problem+json) when a whole service is down

    * configure afterScenario = function(){ docker.start('customer-service') }
    * eval docker.stop('customer-service')
    * configure retry = { count: 20, interval: 1500 }
    Given path '/api/v1/customers/1'
    And retry until responseStatus == 503
    When method get
    Then status 503
    * eval docker.start('customer-service')
    * assert docker.waitUntilHealthy('customer-service', 120)

  # Keep this scenario LAST: the limiter counter is per client IP and shared by every route, so exhausting it early
  # would make the scenarios above get 429s that have nothing to do with what they test.
  Scenario: The gateway answers 429 once a route's per-minute budget is exceeded

    * configure afterScenario = function(){ docker.clearRateLimitKeys() }
    * showcase.event('Firing 60 rapid requests at the payment route (50/min budget); the counter lives in Redis.')
    * def probe = function(){ return karate.call('classpath:e2e/rate-limit-probe.feature', { gatewayUrl: gatewayUrl, authToken: token }) }
    * def statuses = []
    * eval for (var i = 0; i < 60; i++) { statuses.push(probe().responseStatus) }
    * assert statuses.includes(429)
