@ignore
Feature: Wait for an order to reach a status - the saga is asynchronous (called by other features)

  Arguments: token, orderId, expected (a status name; or "TERMINAL" for any final status).
  Optional: attempts (default 60, one per second). Returns: order.

  Scenario:
    * configure headers = { Authorization: '#("Bearer " + token)' }
    * def tries = karate.get('attempts') || 60
    * configure retry = { count: '#(tries)', interval: 1000 }
    * def finals = ['COMPLETED', 'CANCELLED', 'FAILED']
    Given url gatewayUrl
    And path '/api/v1/orders', orderId
    And retry until response.status == expected || (expected == 'TERMINAL' && finals.indexOf(response.status) >= 0)
    When method get
    Then status 200
    * def order = response
