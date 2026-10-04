Feature: Inventory reservation is idempotent against Kafka redelivery
  Proves the fix for a real gap found by auditing this suite's own coverage:
  InventoryEventListener.handleOrderCreated/handlePaymentFailed had no
  protection against Kafka redelivering the same event twice - a normal
  occurrence under AckMode.MANUAL (a consumer restart or rebalance before
  acking is not a failure), not a hypothetical. Before this fix, redelivery
  would decrement (or release) the same order's stock a second time,
  silently - the exact same bug class as the double-payment race already
  fixed in PaymentEventListener (see commit 0c4f488), just on the
  inventory side instead, and never protected the same way.

  The fix: InventoryService.reserveStockIfAvailable/releaseStockIfPresent
  now go through an inventory_reservations table (one row per orderId,
  unique constraint) as an idempotency ledger - reserving inserts that row
  FIRST, inside the same transaction, before ever touching the inventory
  quantity; releasing atomically flips it from active to released. A
  second delivery of the same event hits that row (the insert's unique
  constraint, or the conditional UPDATE already having applied) and
  short-circuits before decrementing/incrementing again.

  Each scenario here publishes the exact same hand-crafted event (same
  eventId, same payload) onto a main topic TWICE in a row via
  KafkaFaultInjector, bypassing order-service/payment-service's own
  producers entirely - faithfully simulating what Kafka redelivery actually
  looks like to this consumer (the identical message, read again), without
  needing to actually kill a consumer mid-processing to provoke it.

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
    * def sleep = function(ms){ Java.type('java.lang.Thread').sleep(ms) }

  Scenario: Redelivering order-created for the same order does not double-decrement stock

    Given path '/api/inventory'
    And param size = 1
    When method get
    Then status 200
    And assert response.content.length >= 1
    * def productId = response.content[0].productId
    * def beforeQty = response.content[0].quantity
    * showcase.event('Starting stock for ' + productId + ': ' + beforeQty + ' units.')

    * def orderId = Java.type('java.lang.System').currentTimeMillis()
    * def eventId = uuid()
    * def payload = '{"eventId":"' + eventId + '","occurredAt":"2026-01-01T00:00:00","aggregateId":"' + orderId + '","aggregateType":"Order","version":1,"orderId":' + orderId + ',"customerId":1,"productId":"' + productId + '","quantity":1}'

    * showcase.event('First delivery: publishing OrderCreatedEvent for order ' + orderId + ' (qty 1).')
    * injector.publishRaw('order-created', orderId + '', 'com.ecommerce.common.events.OrderCreatedEvent', payload)

    * configure retry = { count: 15, interval: 1000 }
    Given path '/api/inventory'
    And param size = 1
    And retry until responseStatus == 200 && response.content[0].quantity == beforeQty - 1
    When method get
    Then status 200
    And match response.content[0].quantity == beforeQty - 1
    * showcase.event('Stock correctly decremented by 1 after the first delivery.')
    * showcase.show('inventory_reservations for order ' + orderId + ' after first delivery', 'inventory_db', 'SELECT order_id, product_id, quantity, released_at FROM inventory_reservations WHERE order_id=' + orderId)

    * showcase.event('Redelivering the EXACT SAME event (identical eventId ' + eventId + ') - this is what a Kafka redelivery looks like to this consumer.')
    * injector.publishRaw('order-created', orderId + '', 'com.ecommerce.common.events.OrderCreatedEvent', payload)
    * sleep(5000)

    Given path '/api/inventory'
    And param size = 1
    When method get
    Then status 200
    And match response.content[0].quantity == beforeQty - 1
    * showcase.event('Stock is STILL beforeQty-1, not beforeQty-2 - the redelivery was correctly absorbed as a no-op instead of decrementing again.')
    * showcase.show('Inventory for ' + productId + ' after redelivery - decremented exactly once', 'inventory_db', "SELECT product_id, quantity FROM inventory WHERE product_id='" + productId + "'")
    * showcase.show('inventory_reservations for order ' + orderId + ' - still exactly one row', 'inventory_db', 'SELECT order_id, product_id, quantity, released_at FROM inventory_reservations WHERE order_id=' + orderId)

  Scenario: Redelivering payment-failed for the same order does not double-release stock

    Given path '/api/inventory'
    And param size = 1
    When method get
    Then status 200
    * def productId = response.content[0].productId
    * def beforeQty = response.content[0].quantity
    * showcase.event('Starting stock for ' + productId + ': ' + beforeQty + ' units.')

    * def orderId = Java.type('java.lang.System').currentTimeMillis()
    * def reserveEventId = uuid()
    * def reservePayload = '{"eventId":"' + reserveEventId + '","occurredAt":"2026-01-01T00:00:00","aggregateId":"' + orderId + '","aggregateType":"Order","version":1,"orderId":' + orderId + ',"customerId":1,"productId":"' + productId + '","quantity":2}'
    * showcase.event('Setting up: reserving 2 units for order ' + orderId + ' first, so there is something to release.')
    * injector.publishRaw('order-created', orderId + '', 'com.ecommerce.common.events.OrderCreatedEvent', reservePayload)

    * configure retry = { count: 15, interval: 1000 }
    Given path '/api/inventory'
    And param size = 1
    And retry until responseStatus == 200 && response.content[0].quantity == beforeQty - 2
    When method get
    Then status 200
    And match response.content[0].quantity == beforeQty - 2
    * showcase.event('2 units reserved for order ' + orderId + ' - now triggering the compensating release.')

    * def releaseEventId = uuid()
    * def releasePayload = '{"eventId":"' + releaseEventId + '","occurredAt":"2026-01-01T00:00:00","aggregateId":"' + orderId + '","aggregateType":"Order","version":1,"orderId":' + orderId + ',"productId":"' + productId + '","quantity":2,"reason":"idempotency test"}'
    * showcase.event('First delivery: publishing PaymentFailedEvent for order ' + orderId + ' - should release the 2 units back.')
    * injector.publishRaw('payment-failed', orderId + '', 'com.ecommerce.common.events.PaymentFailedEvent', releasePayload)

    * configure retry = { count: 15, interval: 1000 }
    Given path '/api/inventory'
    And param size = 1
    And retry until responseStatus == 200 && response.content[0].quantity == beforeQty
    When method get
    Then status 200
    And match response.content[0].quantity == beforeQty
    * showcase.event('Stock correctly released back to the original ' + beforeQty + ' after the first delivery.')
    * showcase.show('inventory_reservations for order ' + orderId + ' after first release', 'inventory_db', 'SELECT order_id, product_id, quantity, released_at FROM inventory_reservations WHERE order_id=' + orderId)

    * showcase.event('Redelivering the EXACT SAME payment-failed event (identical eventId ' + releaseEventId + ').')
    * injector.publishRaw('payment-failed', orderId + '', 'com.ecommerce.common.events.PaymentFailedEvent', releasePayload)
    * sleep(5000)

    Given path '/api/inventory'
    And param size = 1
    When method get
    Then status 200
    And match response.content[0].quantity == beforeQty
    * showcase.event('Stock is STILL exactly ' + beforeQty + ', not beforeQty+2 - the redelivered release was correctly absorbed as a no-op.')
    * showcase.show('Inventory for ' + productId + ' after redelivery - released exactly once', 'inventory_db', "SELECT product_id, quantity FROM inventory WHERE product_id='" + productId + "'")
    * showcase.show('inventory_reservations for order ' + orderId + ' - released_at set exactly once', 'inventory_db', 'SELECT order_id, product_id, quantity, released_at FROM inventory_reservations WHERE order_id=' + orderId)
