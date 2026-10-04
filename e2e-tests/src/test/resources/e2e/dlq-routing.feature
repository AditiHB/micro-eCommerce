Feature: Saga listener failures are routed to the Dead Letter Queue
  Exercises DlqPublisher end-to-end against the live stack: publishes a
  deliberately malformed event straight onto a main topic (bypassing every
  service's own producer via KafkaFaultInjector, a raw Kafka client), which
  makes the real @KafkaListener method that consumes it throw a real
  exception in its actual business logic - then confirms the event lands in
  that topic's own <topic>-dlq instead of being silently dropped, by
  independently consuming the DLQ topic and looking for this test's eventId.

  This is deliberately NOT the same thing as publishing straight to a
  <topic>-dlq topic (which would only prove the DLQ topics/consumer exist,
  not that a listener's own catch block correctly routes to them) - every
  scenario here forces the failure through the listener's real code path.

  Field values the crafted events use (e.g. a null orderId) are never
  reachable through the normal REST-validated flow (CreateOrderRequest,
  ProcessPaymentRequest etc. all reject nulls at the controller boundary) -
  that's the whole point of bypassing the normal producers here.

  Only 5 of the saga's 8 listener methods are covered. The other 3 are
  guarded against exactly this kind of data-only fault injection by design
  (the same defensive checks that make them correct also make them resistant
  to this technique) - forcing a real failure in them would need stopping
  shared infrastructure (e.g. postgres), which this project's own resilience
  testing philosophy already rules out as too broad a blast radius (see
  resilience.feature's own header comment). Not covered here:
    - InventoryEventListener.handlePaymentFailed (payment-failed) - already
      null-checks productId/quantity before doing anything, so there's no
      data value left to send that reaches its business logic unguarded.
    - PaymentEventListener.handleInventoryReserved (inventory-reserved) - a
      null orderId is absorbed by findByOrderId (a derived query, not
      findById - no "id must not be null" check) returning empty rather than
      throwing, and the one write it attempts (the payment insert) can only
      fail via the unique-constraint path, which is explicitly treated as a
      success (idempotent skip), not a failure.
    - PaymentEventListener.handleOrderCancelled (order-cancelled) - same
      findByOrderId behavior; a null/unmatched orderId is handled as a clean
      "no payment found" log, not an exception.

  Background:
    * url gatewayUrl
    Given path '/api/auth/login'
    And request { username: '#(testUsername)', password: '#(testPassword)' }
    When method post
    Then status 200
    * def authToken = response.token
    * configure headers = { Authorization: '#("Bearer " + authToken)' }
    * def injector = Java.type('e2e.KafkaFaultInjector')
    * def showcase = Java.type('e2e.DataShowcase')
    * def uuid = function(){ return Java.type('java.util.UUID').randomUUID() + '' }

  Scenario: InventoryEventListener.handleOrderCreated failure is routed to order-created-dlq
    # A null quantity NPEs on `inventory.getQuantity() >= quantity` - but
    # only once a real product is found, so this needs an existing productId.
    Given path '/api/inventory'
    And param size = 1
    When method get
    Then status 200
    And assert response.content.length >= 1
    * def productId = response.content[0].productId

    * def eventId = uuid()
    * def orderId = Java.type('java.lang.System').currentTimeMillis()
    * def payload = '{"eventId":"' + eventId + '","occurredAt":"2026-01-01T00:00:00","aggregateId":"' + orderId + '","aggregateType":"Order","version":1,"orderId":' + orderId + ',"customerId":1,"productId":"' + productId + '","quantity":null}'
    * showcase.event('Publishing a hand-crafted OrderCreatedEvent for ' + productId + ' with quantity=null - real orders can never have this, since CreateOrderRequest requires a positive quantity.')
    * showcase.showRaw('Crafted event published to order-created (quantity: null)', payload)
    * injector.publishRaw('order-created', orderId + '', 'com.ecommerce.common.events.OrderCreatedEvent', payload)

    * def dlqMessage = injector.findDlqMessage('order-created-dlq', eventId, 20)
    And assert dlqMessage != null
    * showcase.event('InventoryEventListener.handleOrderCreated NPE\'d on `inventory.getQuantity() >= quantity` and DlqPublisher routed it to order-created-dlq instead of silently dropping it.')
    * showcase.showRaw('Message landed in order-created-dlq', dlqMessage)

  Scenario: OrderEventListener.handlePaymentProcessed failure is routed to payment-processed-dlq
    # A null orderId makes orderService.updateOrderStatusIfPresent's
    # orderRepository.findById(null) throw IllegalArgumentException directly
    # (Spring Data JPA's own null-id guard) - no real order needed at all.
    * def eventId = uuid()
    * def payload = '{"eventId":"' + eventId + '","occurredAt":"2026-01-01T00:00:00","aggregateId":"dlq-test","aggregateType":"Payment","version":1,"paymentId":1,"orderId":null,"amount":99.99}'
    * showcase.event('Publishing a hand-crafted PaymentProcessedEvent with orderId=null - no real order needed to trigger this one.')
    * showcase.showRaw('Crafted event published to payment-processed (orderId: null)', payload)
    * injector.publishRaw('payment-processed', 'dlq-test', 'com.ecommerce.common.events.PaymentProcessedEvent', payload)

    * def dlqMessage = injector.findDlqMessage('payment-processed-dlq', eventId, 20)
    And assert dlqMessage != null
    * showcase.event('orderRepository.findById(null) threw IllegalArgumentException (Spring Data JPA\'s own null-id guard), caught by OrderEventListener and routed to payment-processed-dlq.')
    * showcase.showRaw('Message landed in payment-processed-dlq', dlqMessage)

  Scenario: OrderEventListener.handleInventoryFailed failure is routed to inventory-failed-dlq
    * def eventId = uuid()
    * def payload = '{"eventId":"' + eventId + '","occurredAt":"2026-01-01T00:00:00","aggregateId":"dlq-test","aggregateType":"Inventory","version":1,"orderId":null}'
    * showcase.event('Publishing a hand-crafted InventoryFailedEvent with orderId=null.')
    * showcase.showRaw('Crafted event published to inventory-failed (orderId: null)', payload)
    * injector.publishRaw('inventory-failed', 'dlq-test', 'com.ecommerce.common.events.InventoryFailedEvent', payload)

    * def dlqMessage = injector.findDlqMessage('inventory-failed-dlq', eventId, 20)
    And assert dlqMessage != null
    * showcase.event('OrderEventListener.handleInventoryFailed hit the same findById(null) guard and routed to inventory-failed-dlq.')
    * showcase.showRaw('Message landed in inventory-failed-dlq', dlqMessage)

  Scenario: OrderEventListener.handlePaymentFailed failure is routed to payment-failed-dlq
    # productId/quantity are left null too - InventoryEventListener consumes
    # this same topic independently (its own consumer group) and already
    # null-checks those before doing anything, so it just no-ops cleanly
    # rather than also failing. Only OrderEventListener's consumer - which
    # only looks at orderId - is the one meant to throw here.
    * def eventId = uuid()
    * def payload = '{"eventId":"' + eventId + '","occurredAt":"2026-01-01T00:00:00","aggregateId":"dlq-test","aggregateType":"Order","version":1,"orderId":null,"productId":null,"quantity":null,"reason":"dlq-routing test"}'
    * showcase.event('Publishing a hand-crafted PaymentFailedEvent (orderId=null) - productId/quantity are ALSO null on purpose, so InventoryEventListener\'s own independent consumer of this same topic no-ops cleanly instead of also failing.')
    * showcase.showRaw('Crafted event published to payment-failed (orderId: null)', payload)
    * injector.publishRaw('payment-failed', 'dlq-test', 'com.ecommerce.common.events.PaymentFailedEvent', payload)

    * def dlqMessage = injector.findDlqMessage('payment-failed-dlq', eventId, 20)
    And assert dlqMessage != null
    * showcase.event('OrderEventListener\'s consumer of payment-failed threw on the null orderId and routed to payment-failed-dlq.')
    * showcase.showRaw('Message landed in payment-failed-dlq', dlqMessage)

  Scenario: OrderEventListener.handleRefundCompleted failure is routed to refund-completed-dlq
    * def eventId = uuid()
    * def payload = '{"eventId":"' + eventId + '","occurredAt":"2026-01-01T00:00:00","aggregateId":"dlq-test","aggregateType":"Payment","version":1,"orderId":null,"paymentId":1,"refundAmount":10.00}'
    * showcase.event('Publishing a hand-crafted RefundCompletedEvent with orderId=null.')
    * showcase.showRaw('Crafted event published to refund-completed (orderId: null)', payload)
    * injector.publishRaw('refund-completed', 'dlq-test', 'com.ecommerce.common.events.RefundCompletedEvent', payload)

    * def dlqMessage = injector.findDlqMessage('refund-completed-dlq', eventId, 20)
    And assert dlqMessage != null
    * showcase.event('OrderEventListener.handleRefundCompleted\'s repository.findById(null) threw and routed to refund-completed-dlq - the last of the 5 covered listener methods.')
    * showcase.showRaw('Message landed in refund-completed-dlq', dlqMessage)
