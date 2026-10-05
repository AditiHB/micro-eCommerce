Feature: single probe request (no assertions - called in a loop to deliberately exceed a rate limit)

Scenario:
  Given url gatewayUrl
  And path '/api/v1/payments'
  And param page = 0
  And param size = 1
  And header Authorization = 'Bearer ' + authToken
  When method get
