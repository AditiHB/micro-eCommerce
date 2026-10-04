Feature: Customer journey end-to-end
  Exercises the live, already-running Docker Compose stack (no mocks): log
  in, create a customer, browse the inventory catalogue, place an order,
  pay for it, and confirm Notification Service reacted to both the
  order-created and payment-processed Kafka events.

  Background:
    * url gatewayUrl
    * def showcase = Java.type('e2e.DataShowcase')

    # The users table has no self-service registration endpoint and starts
    # empty - "karate_admin" is seeded by a Flyway migration specifically
    # for this (see db/README.md). ADMIN satisfies every role check used
    # below (customers, orders, payments, inventory).
    Given path '/api/auth/login'
    And request { username: '#(testUsername)', password: '#(testPassword)' }
    When method post
    Then status 200
    * def authToken = response.token
    * configure headers = { Authorization: '#("Bearer " + authToken)' }
    * showcase.event('Authenticated as karate_admin (ADMIN) - token acquired for every call below.')

  Scenario: Create a customer, browse the catalogue, order, pay, get notified

    # 1. Create a customer
    * def uniqueId = Java.type('java.util.UUID').randomUUID() + ''
    Given path '/api/customers'
    And request { name: '#("Karate Customer " + uniqueId)', email: '#("karate." + uniqueId + "@example.com")' }
    When method post
    Then status 201
    And match response.id == '#number'
    * def customerId = response.id
    * showcase.event('Customer ' + customerId + ' created - a plain CRUD write, nothing published to Kafka yet.')
    * showcase.show('Customer ' + customerId + ' created', 'customer_db', 'SELECT id, name, email FROM customers WHERE id=' + customerId)

    # 2. See the product catalogue (Inventory Service - product-service
    # isn't part of this Compose stack, see docs/SETUP_AND_DEPLOYMENT.md)
    Given path '/api/inventory'
    And param size = 20
    When method get
    Then status 200
    And assert response.content.length >= 1
    * def catalogueItem = response.content[0]
    * def productId = catalogueItem.productId
    * showcase.event('Picked ' + productId + ' from the catalogue (' + catalogueItem.quantity + ' units on hand) to order against.')

    # 3. Create an order for a product from the catalogue
    Given path '/api/orders'
    And request { customerId: '#(customerId)', productId: '#(productId)', quantity: 1 }
    When method post
    Then status 201
    And match response.customerId == customerId
    And match response.productId == productId
    And match response.status == 'PENDING'
    * def orderId = response.id
    * showcase.event('Order ' + orderId + ' created as PENDING - order-service publishes OrderCreatedEvent. Two independent Kafka consumers react from here: InventoryEventListener (reserve stock) and NotificationEventListener (send "order received").')
    * showcase.show('Order ' + orderId + ' created (PENDING)', 'order_db', 'SELECT id, customer_id, product_id, quantity, status FROM orders WHERE id=' + orderId)
    * showcase.show('Inventory for ' + productId + ' right after order creation', 'inventory_db', "SELECT product_id, quantity FROM inventory WHERE product_id='" + productId + "'")

    # 4. Invoke the Payment Service for that order - this can race the
    # automatic saga (order-created -> inventory-reserved -> an automatic
    # payment via PaymentEventListener.handleInventoryReserved, which in
    # practice reliably wins since it starts as soon as the order is
    # created, well before this test's own sequential HTTP calls catch up).
    # "One payment per order" is enforced atomically (a unique constraint on
    # orderId - see V7__Enforce_One_Payment_Per_Order.sql), so whichever
    # path gets there first succeeds (201) and the other gets a clean 400
    # PAYMENT_ALREADY_EXISTS instead of silently double-charging the order.
    # Either outcome means the order is paid - step 5 is what actually
    # verifies that, regardless of which path got there first.
    Given path '/api/payments'
    And request { orderId: '#(orderId)', amount: 49.99 }
    When method post
    Then assert responseStatus == 201 || responseStatus == 400
    * if (responseStatus == 400 && response.errorCode != 'PAYMENT_ALREADY_EXISTS') karate.fail('unexpected 400 processing payment: ' + JSON.stringify(response))
    * if (responseStatus == 201) showcase.event('This direct POST won the race - it created the payment itself (amount 49.99).')
    * if (responseStatus == 400) showcase.event('PaymentEventListener\'s automatic saga payment won the race instead - this direct POST got a clean 400 PAYMENT_ALREADY_EXISTS (the DB\'s unique constraint on orderId is what actually decides it), not a double charge.')

    # 5. See Notification Service in action - order-created and
    # payment-processed are consumed off Kafka asynchronously, so poll until
    # both notifications for this order have landed.
    * configure retry = { count: 15, interval: 1000 }
    Given url notificationUrl
    And path '/api/notifications/order', orderId
    And retry until responseStatus == 200 && response.length >= 2
    When method get
    Then status 200
    * def orderNotifications = response
    And match orderNotifications == '#[2]'
    * def notificationTypes = karate.jsonPath(orderNotifications, '$[*].type')
    And assert notificationTypes.includes('ORDER_CREATED')
    And assert notificationTypes.includes('PAYMENT_SUCCESS')
    * showcase.event('Saga settled: both notifications delivered, so the order is confirmed paid end to end regardless of which path actually created the payment.')

    # Final state across every service once the saga has settled.
    * showcase.show('Order ' + orderId + ' final state', 'order_db', 'SELECT id, status, updated_at FROM orders WHERE id=' + orderId)
    * showcase.show('Payment for order ' + orderId, 'payment_db', 'SELECT id, order_id, amount, status FROM payments WHERE order_id=' + orderId)
    * showcase.show('Inventory for ' + productId + ' final state', 'inventory_db', "SELECT product_id, quantity FROM inventory WHERE product_id='" + productId + "'")
    * showcase.show('Notifications for order ' + orderId, 'notification_db', 'SELECT id, type, status, subject FROM notifications WHERE order_id=' + orderId + ' ORDER BY id')
