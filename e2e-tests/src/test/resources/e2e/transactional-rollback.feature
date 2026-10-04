Feature: Transactional rollback on constraint violation
  Exercises PaymentService.processPayment's @Transactional behavior against
  the live stack: a second payment for an order that already has one hits
  the database's unique constraint on orderId
  (V7__Enforce_One_Payment_Per_Order.sql) partway through the method, and
  the whole @Transactional method must roll back cleanly - leaving the
  original payment completely untouched and surfacing a clean 400 business
  error instead of a 500 or a silently corrupted row.

  This specifically exercises the flush-vs-save distinction already fixed
  once in this method (see commit 0c4f488, "Fix double-payment race"):
  PaymentService is @Transactional at the class level, so a plain save()
  only queues the INSERT past this method's return/commit - Hibernate
  wouldn't actually run it, and so wouldn't hit the unique constraint,
  until well after this request has already returned. saveAndFlush is what
  forces the INSERT (and therefore the constraint check) to happen
  synchronously, inside this very call, so the resulting
  DataIntegrityViolationException can be caught and converted to a clean
  BusinessException here instead of surfacing later as an untraceable 500.

  Uses a synthetic orderId (not a real order created via order-service) so
  this runs in total isolation from the Kafka saga - no order-created/
  inventory-reserved event ever fires for it, so there's no race with
  PaymentEventListener's own automatic payment creation (see
  customer-journey.feature and resilience.feature for saga-path coverage).
  PaymentService.processPayment never validates the order/inventory side of
  an orderId, so this is a faithful, fully deterministic way to exercise
  this transaction in isolation.

  Background:
    * url gatewayUrl
    * def showcase = Java.type('e2e.DataShowcase')
    Given path '/api/auth/login'
    And request { username: '#(testUsername)', password: '#(testPassword)' }
    When method post
    Then status 200
    * def authToken = response.token
    * configure headers = { Authorization: '#("Bearer " + authToken)' }

  Scenario: Second payment for the same order is rolled back, leaving the original payment untouched

    * def orderId = Java.type('java.lang.System').currentTimeMillis()

    # First payment for this orderId: succeeds normally.
    Given path '/api/payments'
    And request { orderId: '#(orderId)', amount: 49.99 }
    When method post
    Then status 201
    And match response.status == 'PROCESSED'
    * def originalPaymentId = response.id
    * def originalAmount = response.amount
    * showcase.show('Payment ' + originalPaymentId + ' for order ' + orderId + ' - first call, committed', 'payment_db', 'SELECT id, order_id, amount, status FROM payments WHERE order_id=' + orderId)

    # Second payment for the SAME orderId: saveAndFlush's INSERT hits the
    # unique constraint mid-transaction. The whole method rolls back and
    # throws a BusinessException instead of leaving a half-applied row.
    Given path '/api/payments'
    And request { orderId: '#(orderId)', amount: 999.99 }
    When method post
    Then status 400
    And match response.errorCode == 'PAYMENT_ALREADY_EXISTS'

    # Atomicity check: the original payment must be completely unchanged -
    # not overwritten with the second call's amount/PROCESSING status
    # before the constraint fired and the transaction rolled everything
    # back. If the rollback didn't work, this would show 999.99 or a
    # PROCESSING status left behind by the failed second call.
    Given path '/api/payments', originalPaymentId
    When method get
    Then status 200
    And match response.amount == originalAmount
    And match response.status == 'PROCESSED'
    * showcase.show('Payments for order ' + orderId + ' - still exactly one row, unchanged', 'payment_db', 'SELECT id, order_id, amount, status FROM payments WHERE order_id=' + orderId)
