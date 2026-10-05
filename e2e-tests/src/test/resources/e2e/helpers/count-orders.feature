@ignore
Feature: Count a customer's orders (called by other features)

  Arguments: token, customerId. Returns: total.

  The back-office listing is ascending by id and paged, so the newest orders are on the last pages: look at
  the last two pages (200 orders) rather than assuming the customer's orders are on page 0.

  Scenario:
    * configure headers = { Authorization: '#("Bearer " + token)' }
    Given url gatewayUrl
    And path '/api/v1/orders'
    And param size = 100
    And param sortBy = 'id'
    When method get
    Then status 200
    * def lastPage = Math.max(0, Math.floor((response.totalElements - 1) / 100))
    * def pages = lastPage > 0 ? [lastPage, lastPage - 1] : [0]
    * def mine = []
    * def collect =
    """
    function(p) {
      var r = karate.call('classpath:e2e/helpers/orders-page.feature', { token: token, page: p });
      karate.forEach(r.content, function(o){ if (o.customerId == customerId) mine.push(o) });
    }
    """
    * karate.forEach(pages, collect)
    * def total = mine.length
