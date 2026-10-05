Feature: Atomicity and idempotency of placing an order
  An order is one atomic write of the order, its lines, its idempotency key and its order.created event. A request
  that fails validation or refers to something that does not exist leaves nothing behind, and a retried request with
  the same Idempotency-Key returns the original order instead of creating a second one.

  Background:
    * url gatewayUrl
    * def showcase = Java.type('e2e.DataShowcase')
    * def docker = Java.type('e2e.DockerControl')
    * eval docker.clearRateLimitKeys()
    * def login = call read('classpath:e2e/auth.feature') { username: '#(testUsername)', password: '#(testPassword)' }
    * def token = login.accessToken
    * configure headers = { Authorization: '#("Bearer " + token)' }
    * def customer = call read('classpath:e2e/helpers/create-customer.feature') { token: '#(token)' }
    * def customerId = customer.customerId
    * def product = call read('classpath:e2e/helpers/create-product.feature') { token: '#(token)', price: 20.00, stock: 100 }
    * def sku = product.sku

  Scenario: A request that is rejected leaves no order behind

    # unknown product -> 422 with a code, nothing created
    Given path '/api/v1/orders'
    And request { customerId: '#(customerId)', items: [{ productId: 'NO-SUCH-PRODUCT', quantity: 1 }] }
    When method post
    Then status 422
    And match response.errorCode == 'PRODUCT_NOT_FOUND'
    And match response.detail contains 'NO-SUCH-PRODUCT'

    # unknown customer -> 422
    Given path '/api/v1/orders'
    And request { customerId: 999999999, items: [{ productId: '#(sku)', quantity: 1 }] }
    When method post
    Then status 422
    And match response.errorCode == 'CUSTOMER_NOT_FOUND'

    # invalid body -> 400 with the offending fields
    Given path '/api/v1/orders'
    And request { customerId: '#(customerId)', items: [{ productId: '#(sku)', quantity: -3 }] }
    When method post
    Then status 400
    And match response.errorCode == 'VALIDATION_FAILED'

    # a client cannot choose the price
    Given path '/api/v1/orders'
    And request { customerId: '#(customerId)', items: [{ productId: '#(sku)', quantity: 1, unitPrice: 0.01 }], totalAmount: 0.01 }
    When method post
    Then status 400

    # ... and not one of those left a trace
    * def noOrders = call read('classpath:e2e/helpers/count-orders.feature') { token: '#(token)', customerId: '#(customerId)' }
    * match noOrders.total == 0
    * showcase.event('Four rejected requests, zero orders and zero events for customer ' + customerId + '.')

  Scenario: The same Idempotency-Key returns the same order - a retry can never create a second one

    * def key = 'karate-' + java.util.UUID.randomUUID()
    Given path '/api/v1/orders'
    And header Idempotency-Key = key
    And request { customerId: '#(customerId)', items: [{ productId: '#(sku)', quantity: 2 }] }
    When method post
    Then status 201
    * def firstId = response.id
    * def firstLocation = responseHeaders['Location'][0]

    # the client never saw the response (timeout) and simply retries
    Given path '/api/v1/orders'
    And header Idempotency-Key = key
    And request { customerId: '#(customerId)', items: [{ productId: '#(sku)', quantity: 2 }] }
    When method post
    Then status 201
    And match response.id == firstId
    And match responseHeaders['Idempotent-Replayed'][0] == 'true'
    And match responseHeaders['Location'][0] == firstLocation

    # the same key for a DIFFERENT request is an error, not a silent replay
    Given path '/api/v1/orders'
    And header Idempotency-Key = key
    And request { customerId: '#(customerId)', items: [{ productId: '#(sku)', quantity: 9 }] }
    When method post
    Then status 422
    And match response.errorCode == 'IDEMPOTENCY_KEY_REUSED'

    * def orders = call read('classpath:e2e/helpers/count-orders.feature') { token: '#(token)', customerId: '#(customerId)' }
    * match orders.total == 1
    * showcase.show('One order, one order.created event', 'order_db', "SELECT event_type, status FROM outbox_event WHERE aggregate_id='" + firstId + "'")

  Scenario: Many retries with one key still create exactly one order (concurrent duplicates are covered by the Java integration tests)

    * def key = 'karate-race-' + java.util.UUID.randomUUID()
    * def results = karate.repeat(8, function(i){ return karate.call('classpath:e2e/helpers/place-order.feature', { token: token, customerId: customerId, items: [{ productId: sku, quantity: 1 }], idempotencyKey: key }).orderId })
    * match each results == results[0]
    * def orders = call read('classpath:e2e/helpers/count-orders.feature') { token: '#(token)', customerId: '#(customerId)' }
    * match orders.total == 1
