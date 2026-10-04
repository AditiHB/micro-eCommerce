Feature: REST endpoints the saga never exercises
  Every other feature in this module drives the system the way a real
  customer would - through the Kafka saga. This one covers the REST
  endpoints that are real, shipped capabilities but sit outside that path
  entirely: direct admin/ops-style operations nothing else in this suite
  ever calls. Found by auditing this suite's own coverage against every
  REST controller in the codebase.

  Each scenario is independent and narrates what it's proving, with a live
  data showcase at each step - same conventions as every other feature here.

  Background:
    * url gatewayUrl
    * def showcase = Java.type('e2e.DataShowcase')
    Given path '/api/auth/login'
    And request { username: '#(testUsername)', password: '#(testPassword)' }
    When method post
    Then status 200
    * def authToken = response.token
    * configure headers = { Authorization: '#("Bearer " + authToken)' }

  Scenario: GET /auth/me confirms the token is actually recognized as authenticated

    Given path '/api/auth/me'
    When method get
    Then status 200
    * showcase.event('GET /auth/me succeeded - the gateway/service accepted this JWT as a valid, authenticated session.')

  Scenario: Customer lifecycle - read, list, update, delete (only create is exercised elsewhere)

    * def uniqueId = Java.type('java.util.UUID').randomUUID() + ''
    Given path '/api/customers'
    And request { name: '#("Lifecycle Customer " + uniqueId)', email: '#("lifecycle." + uniqueId + "@example.com")' }
    When method post
    Then status 201
    * def customerId = response.id
    * showcase.event('Customer ' + customerId + ' created - now exercising the endpoints no other scenario touches: GET by id, GET list, PUT update, DELETE.')

    Given path '/api/customers', customerId
    When method get
    Then status 200
    And match response.id == customerId
    * showcase.event('GET /api/customers/' + customerId + ' returns the same customer just created.')

    Given path '/api/customers'
    And param size = 5
    When method get
    Then status 200
    And assert response.content.length >= 1
    * showcase.event('GET /api/customers (list) returns a paginated page - ' + response.totalElements + ' customers exist in total.')

    * def updatedEmail = 'updated.' + uniqueId + '@example.com'
    Given path '/api/customers', customerId
    And request { name: '#("Updated Lifecycle Customer " + uniqueId)', email: '#(updatedEmail)' }
    When method put
    Then status 200
    And match response.email == updatedEmail
    * showcase.event('PUT /api/customers/' + customerId + ' changed the email - confirmed in the response.')
    * showcase.show('Customer ' + customerId + ' after update', 'customer_db', 'SELECT id, name, email FROM customers WHERE id=' + customerId)

    Given path '/api/customers', customerId
    When method delete
    Then status 204
    * showcase.event('DELETE /api/customers/' + customerId + ' succeeded (204 No Content).')

    Given path '/api/customers', customerId
    When method get
    Then status 404
    * showcase.event('GET /api/customers/' + customerId + ' now 404s - the delete was real, not just a soft flag the GET ignores.')
    * showcase.show('Customer ' + customerId + ' after delete - expect zero rows', 'customer_db', 'SELECT id, name, email FROM customers WHERE id=' + customerId)

  Scenario: Direct inventory management - create, reserve, insufficient-stock rejection, release, update

    * def uniqueSku = 'SKU-REST-' + Java.type('java.lang.System').currentTimeMillis()
    Given path '/api/inventory'
    And request { productId: '#(uniqueSku)', quantity: 50 }
    When method post
    Then status 201
    * def inventoryId = response.id
    * showcase.event('Created ' + uniqueSku + ' with 50 units via POST /api/inventory - the REST-driven path, entirely separate from the Kafka saga\'s own reserve/release.')
    * showcase.show('Inventory ' + inventoryId + ' created', 'inventory_db', 'SELECT id, product_id, quantity FROM inventory WHERE id=' + inventoryId)

    Given path '/api/inventory', inventoryId, 'reserve'
    And param quantity = 20
    When method post
    Then status 200
    And match response.quantity == 30
    * showcase.event('POST /api/inventory/' + inventoryId + '/reserve?quantity=20 succeeded - 50 -> 30.')

    Given path '/api/inventory', inventoryId, 'reserve'
    And param quantity = 999
    When method post
    Then status 400
    And match response.errorCode == 'INSUFFICIENT_STOCK'
    * showcase.event('Reserving 999 units correctly rejected with 400 INSUFFICIENT_STOCK - stock untouched at 30.')

    Given path '/api/inventory', inventoryId, 'release'
    And param quantity = 20
    When method post
    Then status 200
    And match response.quantity == 50
    * showcase.event('POST /api/inventory/' + inventoryId + '/release?quantity=20 succeeded - back to 50.')

    Given path '/api/inventory', inventoryId
    And param quantity = 200
    When method put
    Then status 200
    And match response.quantity == 200
    * showcase.event('PUT /api/inventory/' + inventoryId + '?quantity=200 directly overwrote the quantity (an admin/ops correction, not a reserve/release delta).')
    * showcase.show('Inventory ' + inventoryId + ' final state', 'inventory_db', 'SELECT id, product_id, quantity FROM inventory WHERE id=' + inventoryId)

  Scenario: Manual order status override bypasses the saga entirely

    * def uniqueId = Java.type('java.util.UUID').randomUUID() + ''
    Given path '/api/customers'
    And request { name: '#("Status Override Customer " + uniqueId)', email: '#("override." + uniqueId + "@example.com")' }
    When method post
    Then status 201
    * def customerId = response.id

    Given path '/api/inventory'
    And param size = 1
    When method get
    Then status 200
    * def productId = response.content[0].productId

    Given path '/api/orders'
    And request { customerId: '#(customerId)', productId: '#(productId)', quantity: 1 }
    When method post
    Then status 201
    And match response.status == 'PENDING'
    * def orderId = response.id
    * showcase.event('Order ' + orderId + ' created as PENDING - instead of waiting for the saga, directly forcing it to COMPLETED via PUT /{id}/status.')

    Given path '/api/orders', orderId, 'status'
    And param status = 'COMPLETED'
    When method put
    Then status 200
    And match response.status == 'COMPLETED'
    * showcase.event('PUT /api/orders/' + orderId + '/status?status=COMPLETED succeeded immediately - no payment was ever processed, this is a direct administrative override.')
    * showcase.show('Order ' + orderId + ' after manual status override', 'order_db', 'SELECT id, status FROM orders WHERE id=' + orderId)

  Scenario: Manual payment refund is a distinct capability from the saga's automatic refund-on-cancellation

    * def orderId = Java.type('java.lang.System').currentTimeMillis()
    Given path '/api/payments'
    And request { orderId: '#(orderId)', amount: 75.00 }
    When method post
    Then status 201
    And match response.status == 'PROCESSED'
    * def paymentId = response.id
    * showcase.event('Payment ' + paymentId + ' PROCESSED for synthetic order ' + orderId + ' - now refunding it directly via POST /{id}/refund, not by cancelling an order (see compensating-transaction.feature for that path instead).')
    * showcase.show('Payment ' + paymentId + ' before refund', 'payment_db', 'SELECT id, order_id, amount, status FROM payments WHERE id=' + paymentId)

    Given path '/api/payments', paymentId, 'refund'
    When method post
    Then status 200
    And match response.status == 'REFUNDED'
    * showcase.event('POST /api/payments/' + paymentId + '/refund succeeded - status is now REFUNDED, independent of any order-cancelled event.')
    * showcase.show('Payment ' + paymentId + ' after refund', 'payment_db', 'SELECT id, order_id, amount, status FROM payments WHERE id=' + paymentId)

  Scenario: Notification lookup endpoints - by id, by customer, and the full list

    * def uniqueId = Java.type('java.util.UUID').randomUUID() + ''
    Given path '/api/customers'
    And request { name: '#("Notification Lookup Customer " + uniqueId)', email: '#("notiflookup." + uniqueId + "@example.com")' }
    When method post
    Then status 201
    * def customerId = response.id

    Given path '/api/inventory'
    And param size = 1
    When method get
    Then status 200
    * def productId = response.content[0].productId

    Given path '/api/orders'
    And request { customerId: '#(customerId)', productId: '#(productId)', quantity: 1 }
    When method post
    Then status 201
    * def orderId = response.id
    * showcase.event('Order ' + orderId + ' created for customer ' + customerId + ' - waiting for the order-created notification to exist, then exercising every lookup endpoint around it.')

    # notification-service isn't routed through the gateway (see
    # customer-journey.feature) - every call below needs its own direct URL.
    * url notificationUrl
    * configure retry = { count: 15, interval: 1000 }
    Given path '/api/notifications/order', orderId
    And retry until responseStatus == 200 && response.length >= 1
    When method get
    Then status 200
    * def notificationId = response[0].id

    Given path '/api/notifications', notificationId
    When method get
    Then status 200
    And match response.id == notificationId
    * showcase.event('GET /api/notifications/' + notificationId + ' returns that exact notification by id.')

    Given path '/api/notifications/customer', customerId
    When method get
    Then status 200
    And assert response.content.length >= 1
    * showcase.event('GET /api/notifications/customer/' + customerId + ' returns ' + response.content.length + ' notification(s) for this customer.')

    Given path '/api/notifications'
    And param size = 5
    When method get
    Then status 200
    And assert response.content.length >= 1
    * showcase.event('GET /api/notifications (list) returns a paginated page across all customers - ' + response.totalElements + ' notifications exist in total.')
