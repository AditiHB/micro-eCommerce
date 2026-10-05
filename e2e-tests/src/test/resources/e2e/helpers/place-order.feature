@ignore
Feature: Place an order (called by other features)

  Arguments: token, customerId, items (list of {productId, quantity}). Optional: idempotencyKey.
  Returns: orderId, order (the 201 response).

  Scenario:
    * configure headers = { Authorization: '#("Bearer " + token)' }
    * def key = karate.get('idempotencyKey')
    * def extra = key ? { 'Idempotency-Key': key } : {}
    * karate.log('placing order for customer', customerId)
    Given url gatewayUrl
    And path '/api/v1/orders'
    And headers extra
    And request { customerId: '#(customerId)', items: '#(items)' }
    When method post
    Then status 201
    * def order = response
    * def orderId = response.id
