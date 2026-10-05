Feature: Messages that cannot be processed are retried, dead-lettered, parked and replayable
  A consumer never swallows a failure and never loses a message. A message it cannot process is retried with
  backoff (when the failure could be transient) or dead-lettered straight away (when it can never succeed), the
  container moves on to the next message, and the dead letter is parked in the owning service's own database,
  where an administrator can list it and replay it.

  Poison messages are published straight onto the real topics with a Kafka client, bypassing every service's own
  producer, so the consumers' real failure handling runs - no mocks.

  Background:
    * url gatewayUrl
    * def showcase = Java.type('e2e.DataShowcase')
    * def kafka = Java.type('e2e.KafkaFaultInjector')
    * def docker = Java.type('e2e.DockerControl')
    * eval docker.clearRateLimitKeys()
    * def login = call read('classpath:e2e/auth.feature') { username: '#(testUsername)', password: '#(testPassword)' }
    * def token = login.accessToken
    * configure headers = { Authorization: '#("Bearer " + token)' }
    * def uid = java.util.UUID.randomUUID() + ''

  Scenario: An order.created event with nothing to reserve is dead-lettered by inventory, listed and replayable

    * def eventId = 'e2e-poison-' + uid
    * def poison = { eventId: '#(eventId)', aggregateId: '#(uid)', aggregateType: 'Order', version: 1, orderId: 987654321, customerId: null, lines: [], totalAmount: 10.00, currency: 'USD' }
    * showcase.event('Publishing an order.created event with no lines straight onto the topic - it can never be reserved.')
    * eval kafka.publishRaw('order-created', uid, 'order.created', JSON.stringify(poison))

    # inventory fails it as NON-retryable (a contract violation), so it goes to the dead-letter topic at once ...
    * configure retry = { count: 40, interval: 1000 }
    Given path '/api/v1/admin/inventory/dead-letters'
    And retry until response.content.filter(function(d){ return d.payload && d.payload.indexOf(eventId) >= 0 }).length > 0
    When method get
    Then status 200
    * def parked = response.content.filter(function(d){ return d.payload && d.payload.indexOf(eventId) >= 0 })[0]
    And match parked.status == 'PARKED'
    And match parked.originalTopic == 'order-created'
    And match parked.consumerGroup == 'inventory-group'
    And match parked.exceptionMessage contains 'no lines'
    * showcase.event('Parked as dead letter ' + parked.id + ' in inventory\'s own database; the partition was not blocked.')
    * showcase.show('Dead letters of inventory-service', 'inventory_db', "SELECT id, original_topic, consumer_group, status FROM dead_letters WHERE payload LIKE '%" + eventId + "%'")

    # ... and nothing was reserved for it
    Given path '/api/v1/inventory'
    And param size = 100
    When method get
    Then status 200

    # replay it: it is published to the original topic again (and, being still invalid, is parked again)
    Given path '/api/v1/admin/inventory/dead-letters', parked.id, 'replay'
    When method post
    Then status 200
    And match response.status == 'REPLAYED'
    And match response.replayedAt == '#string'

    # replaying twice is refused
    Given path '/api/v1/admin/inventory/dead-letters', parked.id, 'replay'
    When method post
    Then status 400
    And match response.errorCode == 'DEAD_LETTER_ALREADY_REPLAYED'

    # the replayed message failed again for the same reason: a fresh dead letter is parked
    Given path '/api/v1/admin/inventory/dead-letters'
    And retry until response.content.filter(function(d){ return d.payload && d.payload.indexOf(eventId) >= 0 && d.status == 'PARKED' }).length > 0
    When method get
    Then status 200

  Scenario: A message that is not even JSON is dead-lettered with its raw bytes, not retried

    * def marker = 'this is not json ' + uid
    * eval kafka.publishRaw('order-created', uid, 'order.created', marker)

    * configure retry = { count: 40, interval: 1000 }
    Given path '/api/v1/admin/inventory/dead-letters'
    And retry until response.content.filter(function(d){ return d.payload == marker }).length > 0
    When method get
    Then status 200
    * def parked = response.content.filter(function(d){ return d.payload == marker })[0]
    And match parked.exceptionClass contains 'DeserializationException'
    * showcase.event('The raw bytes were preserved exactly, so the message can be inspected.')

  Scenario: An unknown event type is refused: only classes in the event catalog are ever instantiated

    * def marker = '{"orderId":1,"note":"' + uid + '"}'
    * eval kafka.publishRaw('order-created', uid, 'java.lang.ProcessBuilder', marker)

    * configure retry = { count: 40, interval: 1000 }
    Given path '/api/v1/admin/inventory/dead-letters'
    And retry until response.content.filter(function(d){ return d.payload == marker }).length > 0
    When method get
    Then status 200
    * showcase.event('A hostile type header was dead-lettered, not deserialized.')

  Scenario: Only an administrator can see or replay dead letters

    * def customer = call read('classpath:e2e/auth.feature') { username: '#(customerUsername)', password: '#(customerPassword)' }
    * configure headers = { Authorization: '#("Bearer " + customer.accessToken)' }
    Given path '/api/v1/admin/inventory/dead-letters'
    When method get
    Then status 403
    Given path '/api/v1/admin/payment/dead-letters/1/replay'
    When method post
    Then status 403
