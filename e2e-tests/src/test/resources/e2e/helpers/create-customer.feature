@ignore
Feature: Create a customer (called by other features)

  Arguments: token. Returns: customerId, email.

  Scenario:
    * def uniqueId = java.util.UUID.randomUUID() + ''
    * def email = 'karate.' + uniqueId + '@example.com'
    * configure headers = { Authorization: '#("Bearer " + token)' }
    Given url gatewayUrl
    And path '/api/v1/customers'
    And request { name: '#("Karate " + uniqueId.substring(0, 8))', email: '#(email)' }
    When method post
    Then status 201
    * def customerId = response.id
