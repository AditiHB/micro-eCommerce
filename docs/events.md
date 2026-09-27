# Event-Driven APIs

The E-Commerce Microservices platform uses Apache Kafka for asynchronous, event-driven communication between services. This document describes all available events.

## Overview

Events enable real-time communication across microservices without tight coupling. When important business events occur, they are published to Kafka topics where other services can subscribe and react.

## Event Architecture

```
Service A (Event Producer)
    ↓
Kafka Cluster (Event Broker)
    ↓ Topic: order-created
    ↓
Service B (Event Consumer) → Action
Service C (Event Consumer) → Action
```

## Event Topics

### Order Service Events

#### order-created
**When:** A new order is created by a customer
**Topic:** `order-created`
**Consumer Group:** `order-group`

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "ORDER_CREATED",
  "timestamp": "2024-01-15T10:30:00Z",
  "source": "order-service",
  "orderId": 1001,
  "customerId": 5,
  "totalAmount": 299.99,
  "items": [
    {
      "productId": 1,
      "quantity": 2,
      "unitPrice": 149.99
    }
  ],
  "shippingAddress": "123 Main St, Anytown, USA"
}
```

**Consumers:**
- Inventory Service → Reserve stock
- Payment Service → Initiate payment
- Notification Service → Send order confirmation

#### order-confirmed
**When:** An order is confirmed and ready for processing
**Topic:** `order-confirmed`

#### order-shipped
**When:** An order is dispatched for delivery
**Topic:** `order-shipped`

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440001",
  "eventType": "ORDER_SHIPPED",
  "timestamp": "2024-01-15T14:20:00Z",
  "source": "order-service",
  "orderId": 1001,
  "customerId": 5,
  "trackingNumber": "1Z999AA10123456784",
  "carrier": "UPS",
  "estimatedDeliveryDate": "2024-01-18"
}
```

#### order-delivered
**When:** An order is delivered to the customer
**Topic:** `order-delivered`

### Inventory Service Events

#### inventory-reserved
**When:** Stock is reserved for an order
**Topic:** `inventory-reserved`
**Consumer Group:** `inventory-group`

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440002",
  "eventType": "INVENTORY_RESERVED",
  "timestamp": "2024-01-15T10:31:00Z",
  "source": "inventory-service",
  "orderId": 1001,
  "inventoryId": 1,
  "productId": 1,
  "quantity": 2,
  "reservationId": "res_123456"
}
```

**Consumers:**
- Order Service → Confirm order reservation
- Payment Service → Proceed with payment

#### inventory-failed
**When:** Stock reservation fails (insufficient stock)
**Topic:** `inventory-failed`

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440003",
  "eventType": "INVENTORY_FAILED",
  "timestamp": "2024-01-15T10:31:30Z",
  "source": "inventory-service",
  "orderId": 1001,
  "inventoryId": 1,
  "productId": 1,
  "requestedQuantity": 5,
  "availableQuantity": 2,
  "reason": "INSUFFICIENT_STOCK"
}
```

**Consumers:**
- Order Service → Mark order as failed
- Notification Service → Send out-of-stock notification

#### inventory-released
**When:** Reserved stock is released back to inventory
**Topic:** `inventory-released`

### Payment Service Events

#### payment-processed
**When:** A payment is successfully processed
**Topic:** `payment-processed`
**Consumer Group:** `payment-group`

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440004",
  "eventType": "PAYMENT_PROCESSED",
  "timestamp": "2024-01-15T10:32:00Z",
  "source": "payment-service",
  "paymentId": 5001,
  "orderId": 1001,
  "customerId": 5,
  "amount": 299.99,
  "currency": "USD",
  "paymentMethod": "CREDIT_CARD",
  "transactionId": "txn_1234567890"
}
```

**Consumers:**
- Order Service → Confirm payment and proceed with fulfillment
- Notification Service → Send payment confirmation
- Analytics Service → Log successful transaction

#### payment-failed
**When:** A payment fails to process
**Topic:** `payment-failed`

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440005",
  "eventType": "PAYMENT_FAILED",
  "timestamp": "2024-01-15T10:32:30Z",
  "source": "payment-service",
  "paymentId": 5001,
  "orderId": 1001,
  "amount": 299.99,
  "reason": "DECLINED",
  "retryable": true
}
```

**Consumers:**
- Order Service → Release reserved inventory
- Notification Service → Send payment failure notice

#### payment-refunded
**When:** A payment is refunded
**Topic:** `payment-refunded`

## Kafka Configuration

### Connection Details

```
Development:
  Bootstrap Servers: localhost:9092
  
Staging:
  Bootstrap Servers: kafka-staging:9092
  
Production:
  Bootstrap Servers: kafka-prod:9092
```

### Consumer Group Configuration

Each service has its own consumer group:

```
order-group          # Order Service consumers
inventory-group      # Inventory Service consumers  
payment-group        # Payment Service consumers
notification-group   # Notification Service consumers
analytics-group      # Analytics Service consumers
```

### Topic Partitioning

| Topic | Partitions | Replication Factor | Retention |
|-------|-----------|-------------------|-----------|
| order-created | 3 | 3 | 7 days |
| inventory-reserved | 3 | 3 | 7 days |
| inventory-failed | 1 | 3 | 7 days |
| payment-processed | 3 | 3 | 30 days |
| payment-failed | 1 | 3 | 30 days |

## Consuming Events

### Java Example

```java
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OrderEventListener {

    @KafkaListener(
        topics = "order-created",
        groupId = "order-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCreated(OrderCreatedEvent event) {
        System.out.println("Order created: " + event.getOrderId());
        
        // Process event
        // - Reserve inventory
        // - Initiate payment
        // - Send confirmation email
    }

    @KafkaListener(
        topics = "inventory-failed",
        groupId = "order-group"
    )
    public void handleInventoryFailed(InventoryFailedEvent event) {
        System.out.println("Inventory failed for order: " + event.getOrderId());
        
        // Mark order as failed
        // Release any reserved resources
        // Send notification to customer
    }
}
```

### Python Example

```python
from kafka import KafkaConsumer
import json
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

consumer = KafkaConsumer(
    'order-created',
    'payment-processed',
    bootstrap_servers=['localhost:9092'],
    group_id='notification-group',
    value_deserializer=lambda m: json.loads(m.decode('utf-8')),
    auto_offset_reset='earliest'
)

for message in consumer:
    event = message.value
    
    if event['eventType'] == 'ORDER_CREATED':
        logger.info(f"Order {event['orderId']} created")
        # Send confirmation email
        
    elif event['eventType'] == 'PAYMENT_PROCESSED':
        logger.info(f"Payment {event['paymentId']} processed")
        # Send receipt email
```

### Node.js Example

```javascript
const { Kafka } = require('kafkajs');

const kafka = new Kafka({
  clientId: 'notification-service',
  brokers: ['localhost:9092']
});

const consumer = kafka.consumer({ groupId: 'notification-group' });

await consumer.connect();
await consumer.subscribe({ topics: ['order-created', 'payment-processed'] });

await consumer.run({
  eachMessage: async ({ topic, partition, message }) => {
    const event = JSON.parse(message.value.toString());
    
    console.log(`Received ${event.eventType} on topic ${topic}`);
    
    if (event.eventType === 'ORDER_CREATED') {
      console.log(`Order ${event.orderId} created`);
      // Send confirmation email
    } else if (event.eventType === 'PAYMENT_PROCESSED') {
      console.log(`Payment ${event.paymentId} processed`);
      // Send receipt
    }
  }
});
```

## Publishing Events

### Java (Spring Kafka)

```java
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderEventPublisher {

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("ORDER_CREATED")
            .timestamp(LocalDateTime.now())
            .orderId(order.getId())
            .customerId(order.getCustomerId())
            .totalAmount(order.getTotalAmount())
            .build();

        kafkaTemplate.send("order-created", event);
    }
}
```

### Python

```python
from kafka import KafkaProducer
import json
import uuid
from datetime import datetime

producer = KafkaProducer(
    bootstrap_servers=['localhost:9092'],
    value_serializer=lambda v: json.dumps(v).encode('utf-8')
)

def publish_order_created(order):
    event = {
        'eventId': str(uuid.uuid4()),
        'eventType': 'ORDER_CREATED',
        'timestamp': datetime.now().isoformat(),
        'source': 'order-service',
        'orderId': order['id'],
        'customerId': order['customer_id'],
        'totalAmount': order['total_amount'],
        'items': order['items']
    }
    
    producer.send('order-created', value=event)
    producer.flush()
```

## Event Ordering and Guarantees

### Delivery Guarantees

- **At-least-once delivery** - Events guaranteed to be delivered but may be duplicated
- **Exactly-once processing** - Implement idempotency in consumers

### Ordering

- Events from same partition maintain order
- Different partitions may process out of order
- Use `eventId` for deduplication

## Monitoring Events

### Kafka Monitoring Tools

**Kafdrop (Web UI)**
```bash
docker run -d \
  --name kafdrop \
  -p 9000:9000 \
  -e KAFKA_BROKERCONNECT=kafka:9092 \
  obsidiandynamics/kafdrop
```

**kafka-console-consumer**
```bash
kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order-created \
  --from-beginning
```

### Metrics to Track

- Consumer lag (delay behind producer)
- Messages per topic per time window
- Error rates by consumer group
- Processing latency

## Best Practices

1. **Idempotent Processing** - Same event processed twice should produce same result
2. **Error Handling** - Dead letter queue for failed events
3. **Versioning** - Version event schema for backward compatibility
4. **Monitoring** - Track consumer lag and processing time
5. **Documentation** - Document event flow and dependencies

## Troubleshooting

### Consumer Lag

Check consumer lag:
```bash
kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group order-group \
  --describe
```

### Missing Events

1. Verify topic has messages
2. Check consumer group offset
3. Confirm subscription to correct topic

### Performance Issues

1. Increase consumer instances (up to partition count)
2. Tune batch sizes and fetch intervals
3. Monitor broker resources

## Event Schema Evolution

When updating event schemas:
1. Add new fields as optional
2. Never remove or rename fields
3. Use versioning if major changes
4. Test with both old and new schemas
5. Deploy consumers before producers

## Related Documentation

- [AsyncAPI Specification](../openapi-spec/asyncapi-events.yaml)
- [Webhooks](webhooks.md) - HTTP-based event notifications
- [Architecture Documentation](getting-started/overview.md)

## Support

For event-related issues:
- **Email**: events-support@ecommerce.local
- **Slack**: #kafka-support
- **Jira**: KAFKA component
