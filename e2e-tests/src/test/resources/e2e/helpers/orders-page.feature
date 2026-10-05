@ignore
Feature: One page of the order listing (called by other features)

  Arguments: token, page. Returns: content.

  Scenario:
    * configure headers = { Authorization: '#("Bearer " + token)' }
    Given url gatewayUrl
    And path '/api/v1/orders'
    And param size = 100
    And param page = page
    And param sortBy = 'id'
    When method get
    Then status 200
    * def content = response.content
