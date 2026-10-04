Feature: Saga compensating transaction on microservice failure
  Exercises the choreography saga's rollback path against the live stack: an
  order is placed for more stock than Inventory Service can fulfil, which
  fails the forward transaction mid-saga and requires undoing the order that
  was already created (a real compensating transaction, not just a
  synchronous validation error).

  The chain, all via Kafka (see OrderEventListener/InventoryEventListener/
  PaymentEventListener's class-level "Compensating Transaction" javadoc):
    1. POST /api/orders creates the order as PENDING and publishes
       OrderCreatedEvent - this already succeeded, so it's the thing that
       needs compensating if a later step fails.
    2. InventoryEventListener.handleOrderCreated finds quantity on hand is
       less than requested and publishes InventoryFailedEvent instead of
       reserving anything (InventoryEventListener.java handleOrderCreated) -
       nothing is decremented on this path, so there is nothing for
       Inventory Service itself to release.
    3. OrderEventListener.handleInventoryFailed consumes that and
       COMPENSATES the order: sets it CANCELLED and publishes
       OrderCancelledEvent (OrderEventListener.java handleInventoryFailed) -
       this is the actual rollback of step 1.
    4. PaymentEventListener.handleOrderCancelled consumes order-cancelled
       looking for a payment to refund (its own compensating transaction for
       a *different* failure point - see resilience.feature's circuit
       breaker scenario), finds none here since payment is never reached
       when inventory reservation fails first, and correctly does nothing.

  This is deliberately a different failure trigger than the payment-failure
  path already covered elsewhere (double-payment fix in PaymentEventListener,
  0c4f488): that one is triggered by an exception processing payment and is
  compensated by releasing reserved inventory; this one is triggered by
  insufficient stock, one step earlier, and is compensated by cancelling the
  order outright since nothing was ever reserved or charged.

  Background:
    * url gatewayUrl
    Given path '/api/auth/login'
    And request { username: '#(testUsername)', password: '#(testPassword)' }
    When method post
    Then status 200
    * def authToken = response.token
    * configure headers = { Authorization: '#("Bearer " + authToken)' }

  Scenario: Ordering more than available stock cancels the order instead of reserving or charging it

    # 1. Pick a real catalogue item and read its current stock, so the
    # requested quantity is guaranteed to exceed it regardless of what other
    # scenarios/runs have already decremented it to.
    Given path '/api/inventory'
    And param size = 20
    When method get
    Then status 200
    And assert response.content.length >= 1
    * def catalogueItem = response.content[0]
    * def inventoryId = catalogueItem.id
    * def productId = catalogueItem.productId
    * def availableQty = catalogueItem.quantity
    * def requestedQty = availableQty + 1000000

    # 2. A customer to place the order as
    * def uniqueId = Java.type('java.util.UUID').randomUUID() + ''
    Given path '/api/customers'
    And request { name: '#("Karate Customer " + uniqueId)', email: '#("karate." + uniqueId + "@example.com")' }
    When method post
    Then status 201
    * def customerId = response.id

    # 3. The forward transaction: order creation succeeds synchronously
    # (order-service doesn't check stock itself - that's Inventory Service's
    # job, asynchronously, over Kafka) and starts out PENDING.
    Given path '/api/orders'
    And request { customerId: '#(customerId)', productId: '#(productId)', quantity: '#(requestedQty)' }
    When method post
    Then status 201
    And match response.status == 'PENDING'
    * def orderId = response.id

    # 4. Poll until the saga's compensating transaction has run: order-service
    # consumes InventoryFailedEvent and cancels the order.
    * configure retry = { count: 15, interval: 1000 }
    Given path '/api/orders', orderId
    And retry until responseStatus == 200 && response.status == 'CANCELLED'
    When method get
    Then status 200
    And match response.status == 'CANCELLED'

    # 5. Confirm Inventory Service's side of the compensation: since nothing
    # was ever reserved on the insufficient-stock path, there's nothing to
    # release either - stock must be exactly what it was before.
    Given path '/api/inventory', inventoryId
    When method get
    Then status 200
    And match response.quantity == availableQty

    # 6. Confirm the saga never reached Payment Service at all - only the
    # order-created notification should exist for this order, never a
    # payment one, since payment is only triggered after a successful
    # inventory reservation that never happened here.
    * configure retry = { count: 15, interval: 1000 }
    Given url notificationUrl
    And path '/api/notifications/order', orderId
    And retry until responseStatus == 200 && response.length >= 1
    When method get
    Then status 200
    * def notificationTypes = karate.jsonPath(response, '$[*].type')
    And assert notificationTypes.includes('ORDER_CREATED')
    And assert !notificationTypes.includes('PAYMENT_SUCCESS')
    And assert !notificationTypes.includes('PAYMENT_FAILED')
