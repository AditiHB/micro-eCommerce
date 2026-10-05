Feature: The saga's failure paths - compensation, end to end
  When a step of the order saga fails, everything an earlier step did is undone. The order service decides to
  cancel and announces order.cancelled; inventory releases its reservation and payment refunds a captured charge.
  These scenarios drive every failure through the live stack: no stock, one short line among several, a payment
  the processor declines, and an order that is already finished.

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

  Scenario: Not enough stock - the order is cancelled, nothing was reserved, payment is never reached

    * def product = call read('classpath:e2e/helpers/create-product.feature') { token: '#(token)', price: 25.00, stock: 2 }
    * def placed = call read('classpath:e2e/helpers/place-order.feature') { token: '#(token)', customerId: '#(customerId)', items: [{ productId: '#(product.sku)', quantity: 5 }] }
    * def orderId = placed.orderId
    * showcase.event('Order ' + orderId + ' wants 5 units of a product that has 2: accepted as PENDING, then inventory.failed will cancel it.')

    * def settled = call read('classpath:e2e/helpers/await-order.feature') { token: '#(token)', orderId: '#(orderId)', expected: 'CANCELLED' }

    Given path '/api/v1/payments/order', orderId
    When method get
    Then status 404
    Given path '/api/v1/inventory', product.inventoryId
    When method get
    Then status 200
    And match response.quantity == 2
    * showcase.event('CANCELLED, stock untouched at 2, and no payment row exists: the saga stopped at the failed step.')

  Scenario: One short line cancels the whole order - reservation is all or nothing

    * def plenty = call read('classpath:e2e/helpers/create-product.feature') { token: '#(token)', price: 5.00, stock: 10 }
    * def scarce = call read('classpath:e2e/helpers/create-product.feature') { token: '#(token)', price: 5.00, stock: 1 }
    * def placed = call read('classpath:e2e/helpers/place-order.feature') { token: '#(token)', customerId: '#(customerId)', items: [{ productId: '#(plenty.sku)', quantity: 4 }, { productId: '#(scarce.sku)', quantity: 3 }] }

    * def settled = call read('classpath:e2e/helpers/await-order.feature') { token: '#(token)', orderId: '#(placed.orderId)', expected: 'CANCELLED' }

    Given path '/api/v1/inventory', plenty.inventoryId
    When method get
    And match response.quantity == 10
    Given path '/api/v1/inventory', scarce.inventoryId
    When method get
    And match response.quantity == 1
    * showcase.event('The line that WAS available (4 of 10) was not reserved either: all-or-nothing.')

  Scenario: The payment processor declines - the order is cancelled, stock comes back, the customer is told

    # The simulated processor declines any charge above 10,000.00.
    * def product = call read('classpath:e2e/helpers/create-product.feature') { token: '#(token)', price: 12000.00, stock: 5 }
    * def placed = call read('classpath:e2e/helpers/place-order.feature') { token: '#(token)', customerId: '#(customerId)', items: [{ productId: '#(product.sku)', quantity: 1 }] }
    * def orderId = placed.orderId

    * def settled = call read('classpath:e2e/helpers/await-order.feature') { token: '#(token)', orderId: '#(orderId)', expected: 'CANCELLED' }

    # the payment exists and says why it failed - a decline is a business outcome, recorded, not an exception
    Given path '/api/v1/payments/order', orderId
    When method get
    Then status 200
    And match response.status == 'FAILED'
    And match response.failureReason contains 'declined'

    # compensation: the stock reserved by step 1 was given back
    * configure retry = { count: 30, interval: 1000 }
    Given path '/api/v1/inventory', product.inventoryId
    And retry until response.quantity == 5
    When method get
    Then status 200

    # a declined payment is not refundable: no money was taken
    Given path '/api/v1/payments/order', orderId
    When method get
    * def failedPaymentId = response.id
    Given path '/api/v1/payments', failedPaymentId, 'refund'
    When method post
    Then status 409
    And match response.errorCode == 'PAYMENT_INVALID_TRANSITION'

    # and the customer is told
    * configure retry = { count: 30, interval: 1000 }
    Given path '/api/v1/notifications/order', orderId
    And retry until responseStatus == 200 && karate.jsonPath(response, '$[*].type').includes('PAYMENT_FAILED')
    When method get
    Then status 200
    * showcase.show('Payment declined for order ' + orderId, 'payment_db', 'SELECT id, order_id, amount, status, failure_reason FROM payments WHERE order_id=' + orderId)
    * showcase.show('Events (order-service)', 'order_db', "SELECT event_type, status FROM outbox_event WHERE aggregate_id='" + orderId + "' ORDER BY id")

  Scenario: A completed order cannot be cancelled; its payment can be refunded once, by back office

    * def product = call read('classpath:e2e/helpers/create-product.feature') { token: '#(token)', price: 40.00, stock: 5 }
    * def placed = call read('classpath:e2e/helpers/place-order.feature') { token: '#(token)', customerId: '#(customerId)', items: [{ productId: '#(product.sku)', quantity: 1 }] }
    * def orderId = placed.orderId
    * def settled = call read('classpath:e2e/helpers/await-order.feature') { token: '#(token)', orderId: '#(orderId)', expected: 'COMPLETED' }

    # COMPLETED is final: cancelling it is a conflict, not a field edit
    Given path '/api/v1/orders', orderId, 'cancel'
    When method post
    Then status 409
    And match response.errorCode == 'ORDER_INVALID_TRANSITION'

    # and a status cannot be forced by hand either
    Given path '/api/v1/orders', orderId, 'status'
    And param status = 'PENDING'
    When method put
    Then status 409
    And match response.errorCode == 'ORDER_STATUS_MANAGED_BY_SAGA'

    # the money: refundable once
    Given path '/api/v1/payments/order', orderId
    When method get
    Then status 200
    * def paymentId = response.id
    Given path '/api/v1/payments', paymentId, 'refund'
    When method post
    Then status 200
    And match response.status == 'REFUNDED'
    Given path '/api/v1/payments', paymentId, 'refund'
    When method post
    Then status 409
    * showcase.event('Refunded once (200), the second attempt refused (409): a payment moves only along CAPTURED -> REFUNDED.')
