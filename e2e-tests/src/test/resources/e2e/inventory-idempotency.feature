Feature: Stock is never oversold, and the last unit is sellable
  Inventory takes stock with a single conditional statement backed by CHECK (quantity >= 0). These scenarios prove
  it through the whole stack: the saga reserving for real orders, and the REST stock operations, under load.

  Background:
    * url gatewayUrl
    * def showcase = Java.type('e2e.DataShowcase')
    * def docker = Java.type('e2e.DockerControl')
    * eval docker.clearRateLimitKeys()
    * def login = call read('classpath:e2e/auth.feature') { username: '#(testUsername)', password: '#(testPassword)' }
    * def token = login.accessToken
    * configure headers = { Authorization: '#("Bearer " + token)' }
    * def customer = call read('classpath:e2e/helpers/create-customer.feature') { token: '#(token)' }
    * def customerId = customer.customerId

  Scenario: The last unit is sellable (it used to fail on the zero and strand the order in PENDING)

    * def product = call read('classpath:e2e/helpers/create-product.feature') { token: '#(token)', price: 9.99, stock: 1 }
    * def placed = call read('classpath:e2e/helpers/place-order.feature') { token: '#(token)', customerId: '#(customerId)', items: [{ productId: '#(product.sku)', quantity: 1 }] }

    * def settled = call read('classpath:e2e/helpers/await-order.feature') { token: '#(token)', orderId: '#(placed.orderId)', expected: 'COMPLETED' }

    Given path '/api/v1/inventory', product.inventoryId
    When method get
    Then status 200
    And match response.quantity == 0
    * showcase.event('Reserved the one remaining unit: stock is exactly 0, the order COMPLETED.')

  Scenario: More orders than stock - exactly the stock is sold, the rest are cancelled, stock ends at zero and never goes negative

    * def product = call read('classpath:e2e/helpers/create-product.feature') { token: '#(token)', price: 5.00, stock: 3 }
    * def sku = product.sku
    * def orderIds = []
    * def place = function(i){ var r = karate.call('classpath:e2e/helpers/place-order.feature', { token: token, customerId: customerId, items: [{ productId: sku, quantity: 1 }] }); orderIds.push(r.orderId); return r.orderId }
    * eval for (var i = 0; i < 6; i++) place(i)
    * match orderIds == '#[6]'

    * def outcomes = []
    * def await = function(id){ var r = karate.call('classpath:e2e/helpers/await-order.feature', { token: token, orderId: id, expected: 'TERMINAL', attempts: 90 }); outcomes.push(r.order.status) }
    * eval for (var j = 0; j < orderIds.length; j++) await(orderIds[j])

    * def completed = karate.filter(outcomes, function(s){ return s == 'COMPLETED' })
    * def cancelled = karate.filter(outcomes, function(s){ return s == 'CANCELLED' })
    * match completed == '#[3]'
    * match cancelled == '#[3]'

    Given path '/api/v1/inventory', product.inventoryId
    When method get
    Then status 200
    And match response.quantity == 0
    * showcase.show('Reservations: exactly 3 units were handed out', 'inventory_db', "SELECT order_id, product_id, quantity, released_at FROM inventory_reservations WHERE product_id='" + sku + "' ORDER BY order_id")

  Scenario: The REST stock operations cannot oversell or invent stock either

    * def product = call read('classpath:e2e/helpers/create-product.feature') { token: '#(token)', price: 1.00, stock: 10 }
    * def inventoryId = product.inventoryId

    # take exactly what is left, in two goes, then ask for one more
    Given path '/api/v1/inventory', inventoryId, 'reserve'
    And param quantity = 6
    When method post
    Then status 200
    And match response.quantity == 4
    Given path '/api/v1/inventory', inventoryId, 'reserve'
    And param quantity = 4
    When method post
    Then status 200
    And match response.quantity == 0
    Given path '/api/v1/inventory', inventoryId, 'reserve'
    And param quantity = 1
    When method post
    Then status 409
    And match response.errorCode == 'INSUFFICIENT_STOCK'

    # a negative reservation used to ADD stock
    Given path '/api/v1/inventory', inventoryId, 'reserve'
    And param quantity = -50
    When method post
    Then status 400
    Given path '/api/v1/inventory', inventoryId
    When method get
    Then status 200
    And match response.quantity == 0

  Scenario: Optimistic concurrency - a stale ETag cannot overwrite a newer stock-take

    * def product = call read('classpath:e2e/helpers/create-product.feature') { token: '#(token)', price: 1.00, stock: 10 }
    Given path '/api/v1/inventory', product.inventoryId
    When method get
    Then status 200
    * def etag = responseHeaders['ETag'][0]

    Given path '/api/v1/inventory', product.inventoryId
    And param quantity = 20
    And header If-Match = etag
    When method put
    Then status 200

    Given path '/api/v1/inventory', product.inventoryId
    And param quantity = 30
    And header If-Match = etag
    When method put
    Then status 412
    And match response.errorCode == 'PRECONDITION_FAILED'

    Given path '/api/v1/inventory', product.inventoryId
    When method get
    And match response.quantity == 20
