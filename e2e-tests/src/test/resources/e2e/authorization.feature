Feature: Authorization end to end - who may do what, with real Keycloak tokens through the gateway

  Proves the access rules against the live stack, not mocks: a plain customer ("karate_user", bound
  to customer id 1 by the customer_id claim in their token) is confined to their own data and kept
  out of every back-office operation, while the back-office admin can do all of it. These are the
  holes found in the engineering review (any logged-in user could refund payments, set stock levels
  and read every customer's orders and personal data) and closed by the role matrix plus
  object-level ownership checks.

  Background:
    * url gatewayUrl
    * def showcase = Java.type('e2e.DataShowcase')
    * def adminLogin = call read('classpath:e2e/auth.feature') { username: '#(testUsername)', password: '#(testPassword)' }
    * def customerLogin = call read('classpath:e2e/auth.feature') { username: '#(customerUsername)', password: '#(customerPassword)' }
    * def adminToken = adminLogin.accessToken
    * def customerToken = customerLogin.accessToken

  Scenario: A customer token is kept out of every back-office endpoint

    * configure headers = { Authorization: '#("Bearer " + customerToken)' }

    Given path '/api/v1/customers'
    When method get
    Then status 403
    * showcase.event('Customer listing (names + emails of everyone) -> 403 for a plain customer.')

    Given path '/api/v1/payments'
    When method get
    Then status 403

    Given path '/api/v1/payments/1/refund'
    When method post
    Then status 403
    * showcase.event('Refunding a payment -> 403 for a plain customer (was open to every logged-in user).')

    Given path '/api/v1/payments'
    And request { orderId: 987654321, amount: 0.01 }
    When method post
    Then status 403
    * showcase.event('There is no charge endpoint at all any more; a customer is refused (403) before routing even matters.')

    Given path '/api/v1/inventory'
    When method get
    Then status 403

    Given path '/api/v1/inventory/1'
    And param quantity = 999999
    When method put
    Then status 403
    * showcase.event('Setting a stock level -> 403 for a plain customer (was open to every logged-in user).')

  Scenario: A customer can read their own customer record and nobody else's

    * configure headers = { Authorization: '#("Bearer " + customerToken)' }

    Given path '/api/v1/customers/1'
    When method get
    Then status 200
    And match response.id == 1

    Given path '/api/v1/customers/2'
    When method get
    Then status 404
    * showcase.event('Customer 2 is not customer 1 -> 404: the record is hidden, its existence is not even confirmed.')

  Scenario: A customer sees and creates only their own orders

    # Back office creates one order for customer 1 and one for customer 2.
    * configure headers = { Authorization: '#("Bearer " + adminToken)' }
    Given path '/api/v1/inventory'
    And param size = 20
    When method get
    Then status 200
    * def productId = response.content[0].productId

    Given path '/api/v1/orders'
    And request { customerId: 1, items: [{ productId: '#(productId)', quantity: 1 }] }
    When method post
    Then status 201
    * def ownOrderId = response.id

    Given path '/api/v1/orders'
    And request { customerId: 2, items: [{ productId: '#(productId)', quantity: 1 }] }
    When method post
    Then status 201
    * def otherOrderId = response.id

    * configure headers = { Authorization: '#("Bearer " + customerToken)' }

    Given path '/api/v1/orders'
    And param size = 100
    When method get
    Then status 200
    * def foreign = karate.filter(response.content, function(o){ return o.customerId != 1 })
    And match foreign == []
    * showcase.event('Order listing as customer 1 contains only customer 1 orders - none of the ' + response.totalElements + ' returned belong to anyone else.')

    Given path '/api/v1/orders', ownOrderId
    When method get
    Then status 200

    Given path '/api/v1/orders', otherOrderId
    When method get
    Then status 404
    * showcase.event('Reading customer 2 order ' + otherOrderId + ' as customer 1 -> 404.')

    Given path '/api/v1/orders'
    And request { customerId: 1, items: [{ productId: '#(productId)', quantity: 1 }] }
    When method post
    Then status 201

    Given path '/api/v1/orders'
    And request { customerId: 2, items: [{ productId: '#(productId)', quantity: 1 }] }
    When method post
    Then status 403
    * showcase.event('Placing an order on behalf of customer 2 as customer 1 -> 403.')

    Given path '/api/v1/orders', ownOrderId, 'status'
    And param status = 'COMPLETED'
    When method put
    Then status 403
    * showcase.event('A customer cannot mark an order COMPLETED themselves -> 403 (back-office only).')

  Scenario: Stock quantities must be positive - a negative reservation can no longer inflate stock

    * configure headers = { Authorization: '#("Bearer " + adminToken)' }
    # A brand-new item, so no order from another scenario (the saga reserves stock asynchronously) can
    # change its level between "before" and the final read.
    * def sku = 'KARATE-' + java.util.UUID.randomUUID()
    Given path '/api/v1/inventory'
    And request { productId: '#(sku)', quantity: 10 }
    When method post
    Then status 201
    * def itemId = response.id
    * def before = response.quantity

    Given path '/api/v1/inventory', itemId, 'reserve'
    And param quantity = -50
    When method post
    Then status 400

    Given path '/api/v1/inventory', itemId, 'release'
    And param quantity = 0
    When method post
    Then status 400

    Given path '/api/v1/inventory', itemId
    And param quantity = -1
    When method put
    Then status 400

    Given path '/api/v1/inventory', itemId
    When method get
    Then status 200
    And match response.quantity == before
    * showcase.event('Negative/zero stock movements -> 400 and the stock level is untouched (it used to rise by 50).')

  Scenario: No token and a non-bearer credential are rejected at the edge

    * configure headers = {}
    Given path '/api/v1/orders'
    When method get
    Then status 401

    Given path '/api/v1/orders'
    And header Authorization = 'Basic YWRtaW46YWRtaW4='
    When method get
    Then status 401
    * showcase.event('Anonymous and Basic-auth requests -> 401; only Keycloak-issued bearer tokens are accepted.')

  Scenario: The retired login endpoint and the old service-name routes no longer exist

    * configure headers = { Authorization: '#("Bearer " + adminToken)' }
    Given path '/api/auth/login'
    And request { username: 'x', password: 'y' }
    When method post
    Then assert responseStatus == 404 || responseStatus == 403

    # The gateway used to publish every service under /<service-id>/** without its filters. Now the
    # gateway refuses it (403); behind nginx (HTTPS overlay) the edge never proxies it at all (404).
    Given path '/order-service/api/orders'
    When method get
    Then assert responseStatus == 403 || responseStatus == 404
    * showcase.event('There is no /api/auth/login and no /order-service/** bypass route any more.')

  Scenario: Dead letters and the operator tooling are admin-only

    * configure headers = { Authorization: '#("Bearer " + customerToken)' }
    Given path '/api/v1/admin/order/dead-letters'
    When method get
    Then status 403
    * configure headers = { Authorization: '#("Bearer " + adminToken)' }
    Given path '/api/v1/admin/order/dead-letters'
    When method get
    Then status 200

  Scenario: A customer can cancel only their own order

    * configure headers = { Authorization: '#("Bearer " + adminToken)' }
    * def product = call read('classpath:e2e/helpers/create-product.feature') { token: '#(adminToken)', price: 5.00, stock: 5 }
    * def otherOrder = call read('classpath:e2e/helpers/place-order.feature') { token: '#(adminToken)', customerId: 2, items: [{ productId: '#(product.sku)', quantity: 1 }] }

    * configure headers = { Authorization: '#("Bearer " + customerToken)' }
    Given path '/api/v1/orders', otherOrder.orderId, 'cancel'
    When method post
    Then status 404
    * showcase.event('Customer 1 trying to cancel customer 2 order -> 404: the order is hidden, not merely forbidden.')
