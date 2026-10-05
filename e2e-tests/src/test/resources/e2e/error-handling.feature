Feature: One error contract across every service
  Every failure - from every service, behind the gateway - is an RFC 9457 problem document
  (application/problem+json) with the same members: type, title, status, detail, instance, errorCode, timestamp.
  A client parses one shape whatever went wrong and whoever answered.

  Background:
    * url gatewayUrl
    * def showcase = Java.type('e2e.DataShowcase')
    * def docker = Java.type('e2e.DockerControl')
    * eval docker.clearRateLimitKeys()
    * def login = call read('classpath:e2e/auth.feature') { username: '#(testUsername)', password: '#(testPassword)' }
    * def token = login.accessToken
    * configure headers = { Authorization: '#("Bearer " + token)' }
    * def problem = { type: '#string', title: '#string', status: '#number', detail: '#string', instance: '#string', errorCode: '#string', timestamp: '#string' }

  Scenario Outline: Unknown ids are 404 problems, whichever service owns the resource

    Given path '<path>'
    When method get
    Then status 404
    And match responseHeaders['Content-Type'][0] contains 'application/problem+json'
    And match response contains problem
    And match response.errorCode == 'RESOURCE_NOT_FOUND'
    And match response.instance == '<path>'
    * showcase.event('GET <path> -> 404 problem+json with errorCode RESOURCE_NOT_FOUND.')

    Examples:
      | path                                  |
      | /api/v1/orders/999999999              |
      | /api/v1/payments/999999999            |
      | /api/v1/customers/999999999           |
      | /api/v1/inventory/999999999           |
      | /api/v1/products/999999999            |
      | /api/v1/notifications/999999999       |

  Scenario: Validation errors name the offending fields, in every service

    Given path '/api/v1/customers'
    And request { name: 'x', email: 'not-an-email' }
    When method post
    Then status 400
    And match response contains problem
    And match response.errorCode == 'VALIDATION_FAILED'
    And match response.errors.email == '#string'
    And match response.errors.name == '#string'

    Given path '/api/v1/orders'
    And request { items: [{ productId: '', quantity: 0 }] }
    When method post
    Then status 400
    And match response.errorCode == 'VALIDATION_FAILED'
    And match response.errors.customerId == '#string'

    Given path '/api/v1/inventory'
    And request { productId: '', quantity: -1 }
    When method post
    Then status 400
    And match response.errors.quantity == '#string'

    Given path '/api/v1/products'
    And request { name: '', price: 0, sku: '', category: '' }
    When method post
    Then status 400
    And match response.errors.price == '#string'

  Scenario: Malformed JSON, a wrong method and a bad parameter are problems too

    Given path '/api/v1/orders'
    And header Content-Type = 'application/json'
    And request '{ this is not json'
    When method post
    Then status 400
    And match response.errorCode == 'MALFORMED_REQUEST'

    Given path '/api/v1/payments'
    And request { orderId: 1, amount: 0.01 }
    When method post
    Then status 405
    And match response contains problem
    And match response.errorCode == 'METHOD_NOT_ALLOWED'

    Given path '/api/v1/inventory/1/reserve'
    And param quantity = 'abc'
    When method post
    Then status 400
    And match response.errorCode == '#string'

    Given path '/api/v1/orders'
    And param sortBy = 'customer.password'
    When method get
    Then status 400
    And match response.errorCode == 'INVALID_SORT_FIELD'

  Scenario: Conflicts (409) and failed preconditions (412) are problems

    * def customer = call read('classpath:e2e/helpers/create-customer.feature') { token: '#(token)' }
    Given path '/api/v1/customers'
    And request { name: 'Duplicate Email', email: '#(customer.email)' }
    When method post
    Then status 409
    And match response.errorCode == 'CUSTOMER_EMAIL_EXISTS'

    Given path '/api/v1/customers', customer.customerId
    And header If-Match = '"999"'
    And request { name: 'Someone Else', email: '#(customer.email)' }
    When method put
    Then status 412
    And match response.errorCode == 'PRECONDITION_FAILED'

  Scenario: No token and a Basic credential are refused at the edge

    * configure headers = {}
    Given path '/api/v1/orders'
    When method get
    Then status 401
    Given path '/api/v1/orders'
    And header Authorization = 'Basic YWRtaW46YWRtaW4='
    When method get
    Then status 401
