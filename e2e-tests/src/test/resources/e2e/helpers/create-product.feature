@ignore
Feature: Create a sellable product - a catalogue entry with a price, plus its stock (called by other features)

  Arguments: token, price, stock. Returns: sku, productId (catalogue id), inventoryId.

  Each call creates a brand-new, uniquely-named product, so scenarios never compete for the same stock.

  Scenario:
    * def sku = 'KARATE-' + java.util.UUID.randomUUID()
    * configure headers = { Authorization: '#("Bearer " + token)' }
    Given url gatewayUrl
    And path '/api/v1/products'
    And request { name: '#("Karate product " + sku.substring(7, 15))', price: '#(price)', sku: '#(sku)', category: 'Karate', currency: 'USD' }
    When method post
    Then status 201
    * def productId = response.id

    Given url gatewayUrl
    And path '/api/v1/inventory'
    And request { productId: '#(sku)', quantity: '#(stock)' }
    When method post
    Then status 201
    * def inventoryId = response.id
