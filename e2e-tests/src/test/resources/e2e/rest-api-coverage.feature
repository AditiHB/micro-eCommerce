Feature: REST API coverage - every public endpoint, through the gateway
  One pass over each resource's API: customers, products (the catalogue), inventory, orders, payments and
  notifications, plus the versioning contract (v1 is canonical; the old unversioned paths still work but say they
  are deprecated). Business flows are in the other features; this one makes sure every endpoint is reachable and
  behaves as documented.

  Background:
    * url gatewayUrl
    * def docker = Java.type('e2e.DockerControl')
    * eval docker.clearRateLimitKeys()
    * def login = call read('classpath:e2e/auth.feature') { username: '#(testUsername)', password: '#(testPassword)' }
    * def token = login.accessToken
    * configure headers = { Authorization: '#("Bearer " + token)' }

  Scenario: Customers - create, read, update with If-Match, list, delete

    * def uniq = java.util.UUID.randomUUID() + ''
    Given path '/api/v1/customers'
    And request { name: 'Coverage Customer', email: '#("cov." + uniq + "@example.com")' }
    When method post
    Then status 201
    And match header ETag == '"0"'
    And match header Location == '#regex /api/v1/customers/\\d+'
    * def id = response.id

    Given path '/api/v1/customers', id
    When method get
    Then status 200
    And match response == { id: '#(id)', name: 'Coverage Customer', email: '#string', version: 0, createdAt: '#string', updatedAt: '#string' }

    Given path '/api/v1/customers', id
    And header If-Match = '"0"'
    And request { name: 'Renamed Customer', email: '#("cov." + uniq + "@example.com")' }
    When method put
    Then status 200
    And match response.name == 'Renamed Customer'
    And match header ETag == '"1"'

    Given path '/api/v1/customers'
    And param sortBy = 'email'
    And param size = 5
    When method get
    Then status 200
    And match response.content == '#[_ <= 5]'

    Given path '/api/v1/customers', id
    When method delete
    Then status 204
    Given path '/api/v1/customers', id
    When method get
    Then status 404

  Scenario: Products - the catalogue: create, read by id and SKU, batch lookup, search, update, delete

    * def sku = 'COV-' + java.util.UUID.randomUUID()
    Given path '/api/v1/products'
    And request { name: 'Coverage Gadget', description: 'for the coverage test', price: 19.99, sku: '#(sku)', category: 'Coverage' }
    When method post
    Then status 201
    And match response.currency == 'USD'
    And match response.quantityAvailable == '#notpresent'
    * def id = response.id

    Given path '/api/v1/products', id
    When method get
    Then status 200
    And match response.sku == sku
    Given path '/api/v1/products/sku', sku
    When method get
    Then status 200

    # one call prices several SKUs; unknown ones are simply absent
    Given path '/api/v1/products/lookup'
    And param skus = sku + ',DOES-NOT-EXIST'
    When method get
    Then status 200
    And match response == '#[1]'
    And match response[0].price == 19.99

    Given path '/api/v1/products/search'
    And param term = 'coverage gadget'
    When method get
    Then status 200
    And match response.content[*].sku contains sku
    Given path '/api/v1/products/category/Coverage'
    When method get
    Then status 200

    Given path '/api/v1/products', id
    And request { price: 24.50 }
    When method put
    Then status 200
    And match response.price == 24.50
    And match response.name == 'Coverage Gadget'

    # a client cannot sort the catalogue by an arbitrary property
    Given path '/api/v1/products'
    And param sort = 'description,desc'
    When method get
    Then status 400
    And match response.errorCode == 'INVALID_SORT_FIELD'

    # a duplicate SKU is a conflict
    Given path '/api/v1/products'
    And request { name: 'Again', price: 1.00, sku: '#(sku)', category: 'Coverage' }
    When method post
    Then status 409
    And match response.errorCode == 'DUPLICATE_SKU'

    Given path '/api/v1/products', id
    When method delete
    Then status 204

  Scenario: Inventory - create (including zero stock), read, reserve/release, stock-take

    * def sku = 'COV-INV-' + java.util.UUID.randomUUID()
    Given path '/api/v1/inventory'
    And request { productId: '#(sku)', quantity: 0 }
    When method post
    Then status 201
    And match response.quantity == 0
    * def id = response.id

    Given path '/api/v1/inventory', id
    And param quantity = 25
    When method put
    Then status 200
    And match response.quantity == 25
    Given path '/api/v1/inventory', id, 'reserve'
    And param quantity = 5
    When method post
    Then status 200
    And match response.quantity == 20
    Given path '/api/v1/inventory', id, 'release'
    And param quantity = 2
    When method post
    Then status 200
    And match response.quantity == 22

    Given path '/api/v1/inventory'
    And param sortBy = 'quantity'
    When method get
    Then status 200

  Scenario: Orders, payments and notifications - list and read

    * def customer = call read('classpath:e2e/helpers/create-customer.feature') { token: '#(token)' }
    * def product = call read('classpath:e2e/helpers/create-product.feature') { token: '#(token)', price: 8.00, stock: 10 }
    * def placed = call read('classpath:e2e/helpers/place-order.feature') { token: '#(token)', customerId: '#(customer.customerId)', items: [{ productId: '#(product.sku)', quantity: 2 }] }
    * def orderId = placed.orderId
    * def settled = call read('classpath:e2e/helpers/await-order.feature') { token: '#(token)', orderId: '#(orderId)', expected: 'COMPLETED' }

    # the original single-product request shape is still accepted
    Given path '/api/v1/orders'
    And request { customerId: '#(customer.customerId)', productId: '#(product.sku)', quantity: 1 }
    When method post
    Then status 201
    And match response.items == '#[1]'

    Given path '/api/v1/orders'
    And param size = 5
    And param sortBy = 'createdAt'
    When method get
    Then status 200
    And match response.content == '#[_ <= 5]'

    Given path '/api/v1/payments'
    And param size = 5
    When method get
    Then status 200
    Given path '/api/v1/payments/order', orderId
    When method get
    Then status 200
    * def paymentId = response.id
    Given path '/api/v1/payments', paymentId
    When method get
    Then status 200
    And match header ETag == '#string'

    * configure retry = { count: 30, interval: 1000 }
    Given path '/api/v1/notifications/order', orderId
    And retry until responseStatus == 200 && response.length >= 2
    When method get
    Then status 200
    * def notificationId = response[0].id
    Given path '/api/v1/notifications', notificationId
    When method get
    Then status 200
    Given path '/api/v1/notifications/customer', customer.customerId
    When method get
    Then status 200
    Given path '/api/v1/notifications'
    And param sortBy = 'createdAt'
    When method get
    Then status 200

  Scenario: Versioning - v1 is canonical, the old unversioned paths still work but are marked deprecated

    Given path '/api/customers'
    And param size = 1
    When method get
    Then status 200
    And match responseHeaders['Deprecation'][0] == 'true'
    And match responseHeaders['Link'][0] contains '/api/v1/customers'

    Given path '/api/v1/customers'
    And param size = 1
    When method get
    Then status 200
    And match responseHeaders contains { 'Deprecation': '#notpresent' }

    # the service-name routes are gone
    Given path '/customer-service/api/customers'
    When method get
    Then assert responseStatus == 403 || responseStatus == 404
