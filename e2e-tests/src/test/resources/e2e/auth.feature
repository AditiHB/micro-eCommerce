@ignore
Feature: Obtain an access token from Keycloak (called by the other features, never run on its own)

  The platform has no login endpoint of its own any more: Keycloak is the only thing that issues
  tokens. The development-only client "ecommerce-e2e" (created by infrastructure/keycloak/seed-dev.sh)
  is allowed the password grant so automated tests can sign in as one of the seeded test users.

  Scenario: Password grant
    Given url keycloakTokenUrl
    And form field grant_type = 'password'
    And form field client_id = e2eClientId
    And form field client_secret = e2eClientSecret
    And form field username = username
    And form field password = password
    When method post
    Then status 200
    * def accessToken = response.access_token
