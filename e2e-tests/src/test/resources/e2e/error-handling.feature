Feature: Authentication, not-found and validation errors are clean, not 500s
  Every other feature in this module drives the happy path or a specific
  business failure (insufficient stock, a DB constraint, a saga rollback).
  None of them ever check the basic contract every REST API needs to hold:
  a missing/bad token, a nonexistent id, or malformed input should come
  back as a clean 401/404/400 with a useful body - never an opaque 500.
  Found by auditing this suite's own coverage; nothing here was exercised
  anywhere else.

  Background:
    * url gatewayUrl
    * def showcase = Java.type('e2e.DataShowcase')
    Given path '/api/auth/login'
    And request { username: '#(testUsername)', password: '#(testPassword)' }
    When method post
    Then status 200
    * def authToken = response.token
    * configure headers = { Authorization: '#("Bearer " + authToken)' }

  Scenario: Requests without a valid JWT are rejected with 401, not silently allowed

    * configure headers = {}
    Given path '/api/orders'
    When method get
    Then status 401
    * showcase.event('GET /api/orders with NO Authorization header at all -> 401.')

    Given path '/api/customers'
    And header Authorization = 'Bearer this-is-not-a-real-token'
    When method get
    Then status 401
    * showcase.event('GET /api/customers with a garbage/unparseable JWT -> 401, not a 500 from a failed token parse.')

  Scenario: GET for a nonexistent id returns a clean 404 across every service

    Given path '/api/orders/999999999'
    When method get
    Then status 404
    * showcase.event('GET /api/orders/999999999 -> 404.')

    Given path '/api/payments/999999999'
    When method get
    Then status 404
    * showcase.event('GET /api/payments/999999999 -> 404.')

    Given path '/api/customers/999999999'
    When method get
    Then status 404
    * showcase.event('GET /api/customers/999999999 -> 404.')

    Given path '/api/inventory/999999999'
    When method get
    Then status 404
    * showcase.event('GET /api/inventory/999999999 -> 404.')

    * url notificationUrl
    Given path '/api/notifications/999999999'
    When method get
    Then status 404
    * showcase.event('GET /api/notifications/999999999 -> 404 - every GET-by-id endpoint in the system behaves the same way for a missing row.')

  Scenario: Malformed input is rejected with a clean 400 before anything is persisted

    Given url gatewayUrl
    And path '/api/customers'
    And request { name: 'Bad Email Customer', email: 'not-a-valid-email' }
    When method post
    Then status 400
    * showcase.event('POST /api/customers with an invalid email -> 400 VALIDATION_FAILED, nothing persisted.')

    Given path '/api/orders'
    And request { customerId: 1, productId: 'SKU-001', quantity: -5 }
    When method post
    Then status 400
    * showcase.event('POST /api/orders with a negative quantity -> 400 - rejected at the controller boundary before OrderCreatedEvent is ever published.')

    * def uniqueSku = 'SKU-BAD-' + Java.type('java.lang.System').currentTimeMillis()
    Given path '/api/inventory'
    And request { productId: '#(uniqueSku)', quantity: 0 }
    When method post
    Then status 400
    * showcase.event('POST /api/inventory with a zero quantity -> 400 (quantity must be positive).')

    Given path '/api/payments'
    And request { orderId: 123456789, amount: -10.00 }
    When method post
    Then status 400
    * showcase.event('POST /api/payments with a negative amount -> 400, no payment row created for order 123456789.')
    * showcase.show('Payments for order 123456789 - expect zero rows', 'payment_db', 'SELECT id, order_id, amount, status FROM payments WHERE order_id=123456789')

  Scenario: Logging in with the wrong password is rejected cleanly, not with a token

    * configure headers = {}
    Given path '/api/auth/login'
    And request { username: '#(testUsername)', password: 'definitely-the-wrong-password' }
    When method post
    Then status 400
    * showcase.event('Login with a wrong password -> 400, no JWT issued.')
