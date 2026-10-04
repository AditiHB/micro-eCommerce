Feature: Customer journey end-to-end
  Exercises the live, already-running Docker Compose stack (no mocks): log
  in, create a customer, browse the inventory catalogue, place an order,
  pay for it, and confirm Notification Service reacted to both the
  order-created and payment-processed Kafka events.

  Background:
    * url gatewayUrl

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

  Scenario: Create a customer, browse the catalogue, order, pay, get notified

    # 1. Create a customer
    * def uniqueId = Java.type('java.util.UUID').randomUUID() + ''
    Given path '/api/customers'
    And request { name: '#("Karate Customer " + uniqueId)', email: '#("karate." + uniqueId + "@example.com")' }
    When method post
    Then status 201
    And match response.id == '#number'
    * def customerId = response.id

    # 2. See the product catalogue (Inventory Service - product-service
    # isn't part of this Compose stack, see docs/SETUP_AND_DEPLOYMENT.md)
    Given path '/api/inventory'
    And param size = 20
    When method get
    Then status 200
    And assert response.content.length >= 1
    * def catalogueItem = response.content[0]
    * def productId = catalogueItem.productId

    # 3. Create an order for a product from the catalogue
    Given path '/api/orders'
    And request { customerId: '#(customerId)', productId: '#(productId)', quantity: 1 }
    When method post
    Then status 201
    And match response.customerId == customerId
    And match response.productId == productId
    And match response.status == 'PENDING'
    * def orderId = response.id

    # 4. Invoke the Payment Service for that order
    Given path '/api/payments'
    And request { orderId: '#(orderId)', amount: 49.99 }
    When method post
    Then status 201
    And match response.orderId == orderId
    And match response.status == 'PROCESSED'

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
