Feature: Customer journey end-to-end
  Exercises the live, already-running Docker Compose stack (no mocks): sign in, create a customer, put a priced
  product into the catalogue, place an order, and watch the choreography saga run by itself across inventory,
  payment and notification - charging the order's REAL total (taken from the catalogue, never from the request)
  and ending COMPLETED.

  Background:
    * url gatewayUrl
    * def showcase = Java.type('e2e.DataShowcase')
    * def docker = Java.type('e2e.DockerControl')
    * eval docker.clearRateLimitKeys()

    # Tokens come from Keycloak (the platform's only identity provider). "karate_admin" is a development-only user
    # created by infrastructure/keycloak/seed-dev.sh with a password from your git-ignored .env.
    * def login = call read('classpath:e2e/auth.feature') { username: '#(testUsername)', password: '#(testPassword)' }
    * def token = login.accessToken
    * configure headers = { Authorization: '#("Bearer " + token)' }
    * showcase.event('Authenticated as karate_admin (ADMIN) - token acquired for every call below.')

  Scenario: Order, pay the real total, get notified

    * def customer = call read('classpath:e2e/helpers/create-customer.feature') { token: '#(token)' }
    * def customerId = customer.customerId
    * def product = call read('classpath:e2e/helpers/create-product.feature') { token: '#(token)', price: 79.99, stock: 10 }
    * def sku = product.sku
    * showcase.event('Customer ' + customerId + ' and product ' + sku + ' (79.99 USD, 10 in stock) created.')

    # --- place the order: the server prices it from the catalogue ---------------------------------------------
    Given path '/api/v1/orders'
    And request { customerId: '#(customerId)', items: [{ productId: '#(sku)', quantity: 3 }] }
    When method post
    Then status 201
    And match header Location == '#regex /api/v1/orders/\\d+'
    And match header ETag == '"0"'
    And match response.status == 'PENDING'
    And match response.currency == 'USD'
    And match response.totalAmount == 239.97
    And match response.items == [{ productId: '#(sku)', quantity: 3, unitPrice: 79.99, lineTotal: 239.97 }]
    * def orderId = response.id
    * showcase.event('Order ' + orderId + ' accepted as PENDING, total 239.97 = 3 x 79.99, priced by the server. order-service wrote the order AND its order.created event in one transaction; the outbox relay now delivers it to Kafka.')

    # --- the saga runs by itself ------------------------------------------------------------------------------
    * def settled = call read('classpath:e2e/helpers/await-order.feature') { token: '#(token)', orderId: '#(orderId)', expected: 'COMPLETED' }
    * showcase.event('Saga settled: inventory reserved 3 units -> payment captured -> order COMPLETED, all via events.')

    # --- payment: the real total, captured -------------------------------------------------------------------
    Given path '/api/v1/payments/order', orderId
    When method get
    Then status 200
    And match response.status == 'CAPTURED'
    And match response.amount == 239.97
    And match response.currency == 'USD'
    And match response.customerId == customerId
    And match response.processorReference == '#string'

    # --- inventory: stock went 10 -> 7 ------------------------------------------------------------------------
    Given path '/api/v1/inventory', product.inventoryId
    When method get
    Then status 200
    And match response.quantity == 7

    # --- notifications: both delivered (recorded in the same transaction as the event, sent by the dispatcher) -
    * configure retry = { count: 30, interval: 1000 }
    Given path '/api/v1/notifications/order', orderId
    And retry until responseStatus == 200 && response.length == 2 && karate.jsonPath(response, "$[?(@.status=='SENT')]").length == 2
    When method get
    Then status 200
    * def types = karate.jsonPath(response, '$[*].type')
    And assert types.includes('ORDER_CREATED')
    And assert types.includes('PAYMENT_SUCCESS')

    # --- the order and its audit trail ------------------------------------------------------------------------
    Given path '/api/v1/orders', orderId
    When method get
    Then status 200
    And match response.status == 'COMPLETED'
    And match response.version == '#number'
    * showcase.show('Order ' + orderId + ' final state', 'order_db', 'SELECT id, status, total_amount, currency, version FROM orders WHERE id=' + orderId)
    * showcase.show('Order lines', 'order_db', 'SELECT order_id, line_no, product_id, quantity, unit_price FROM order_lines WHERE order_id=' + orderId)
    * showcase.show('Payment for order ' + orderId, 'payment_db', 'SELECT id, order_id, amount, currency, status, processor_reference FROM payments WHERE order_id=' + orderId)
    * showcase.show('Events order-service relayed (outbox)', 'order_db', "SELECT event_type, topic, status, attempts FROM outbox_event WHERE aggregate_id='" + orderId + "' ORDER BY id")
    * showcase.show('Events payment-service relayed (outbox)', 'payment_db', "SELECT event_type, topic, status FROM outbox_event WHERE aggregate_id='" + orderId + "' ORDER BY id")
    * showcase.show('Inventory for ' + sku, 'inventory_db', "SELECT product_id, quantity, version FROM inventory WHERE product_id='" + sku + "'")
    * showcase.show('Notifications for order ' + orderId, 'notification_db', 'SELECT type, status, attempts FROM notifications WHERE order_id=' + orderId + ' ORDER BY id')

  Scenario: A multi-line order is priced line by line and settles the same way

    * def customer = call read('classpath:e2e/helpers/create-customer.feature') { token: '#(token)' }
    * def productA = call read('classpath:e2e/helpers/create-product.feature') { token: '#(token)', price: 10.00, stock: 20 }
    * def productB = call read('classpath:e2e/helpers/create-product.feature') { token: '#(token)', price: 2.50, stock: 20 }

    Given path '/api/v1/orders'
    And request { customerId: '#(customer.customerId)', items: [{ productId: '#(productA.sku)', quantity: 2 }, { productId: '#(productB.sku)', quantity: 4 }] }
    When method post
    Then status 201
    And match response.totalAmount == 30.00
    And match response.items == '#[2]'
    * def orderId = response.id

    * def settled = call read('classpath:e2e/helpers/await-order.feature') { token: '#(token)', orderId: '#(orderId)', expected: 'COMPLETED' }

    Given path '/api/v1/inventory', productA.inventoryId
    When method get
    And match response.quantity == 18
    Given path '/api/v1/inventory', productB.inventoryId
    When method get
    And match response.quantity == 16
    * showcase.event('Both lines reserved and charged as one payment of 30.00.')
