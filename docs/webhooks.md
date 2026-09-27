# Webhook Documentation

Learn how to receive real-time event notifications through webhooks when important events occur in the E-Commerce Microservices platform.

## Overview

Webhooks provide a way for the E-Commerce API to notify your application of events in real-time. Instead of polling the API repeatedly, you can configure webhooks to push notifications to your application when events occur.

## How Webhooks Work

1. You register a webhook URL with the E-Commerce API
2. When an event occurs (e.g., order created), the API sends an HTTP POST request to your URL
3. Your application processes the webhook payload
4. You send a 200 OK response to confirm receipt
5. If delivery fails, the API automatically retries with exponential backoff

## Supported Events

### Order Events

- `order.created` - New order created
- `order.confirmed` - Order confirmed
- `order.shipped` - Order shipped
- `order.delivered` - Order delivered
- `order.cancelled` - Order cancelled

### Payment Events

- `payment.processed` - Payment successfully processed
- `payment.failed` - Payment processing failed
- `payment.refunded` - Payment refunded

### Inventory Events

- `inventory.reserved` - Stock reserved
- `inventory.released` - Stock released
- `inventory.low_stock` - Stock level below reorder point

### Customer Events

- `customer.created` - New customer created
- `customer.updated` - Customer information updated
- `customer.deleted` - Customer deleted

## Webhook Payload

All webhook payloads follow this structure:

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "order.created",
  "timestamp": "2024-01-15T10:30:00Z",
  "data": {
    "orderId": 1001,
    "customerId": 5,
    "totalAmount": 299.99,
    "items": [
      {
        "productId": 1,
        "quantity": 2,
        "unitPrice": 149.99
      }
    ]
  },
  "retryCount": 0
}
```

### Payload Fields

| Field | Type | Description |
|-------|------|-------------|
| `eventId` | UUID | Unique identifier for this webhook delivery |
| `eventType` | string | Type of event (see supported events above) |
| `timestamp` | ISO 8601 | When the event occurred |
| `data` | object | Event-specific data (varies by event type) |
| `retryCount` | integer | Number of delivery attempts (0 for first attempt) |

## Registering Webhooks

### Via API (Recommended)

```bash
curl -X POST https://api.ecommerce.local/api/webhooks \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "url": "https://yourapp.example.com/webhooks/ecommerce",
    "events": [
      "order.created",
      "order.shipped",
      "payment.processed"
    ],
    "active": true
  }'
```

### Webhook Configuration Response

```json
{
  "id": "wh_1234567890",
  "url": "https://yourapp.example.com/webhooks/ecommerce",
  "events": [
    "order.created",
    "order.shipped",
    "payment.processed"
  ],
  "active": true,
  "createdAt": "2024-01-15T10:30:00Z",
  "secret": "whsec_test_secret_key"
}
```

## Receiving Webhooks

### Node.js/Express Example

```javascript
const express = require('express');
const crypto = require('crypto');

const app = express();
app.use(express.json());

const WEBHOOK_SECRET = 'whsec_test_secret_key'; // From webhook registration

app.post('/webhooks/ecommerce', (req, res) => {
  const signature = req.headers['x-webhook-signature'];
  const timestamp = req.headers['x-webhook-timestamp'];

  // Verify signature
  if (!verifySignature(req.body, signature, timestamp)) {
    return res.status(401).send('Unauthorized');
  }

  const { eventId, eventType, data } = req.body;

  console.log(`Received webhook: ${eventType} (${eventId})`);

  // Handle different event types
  switch (eventType) {
    case 'order.created':
      handleOrderCreated(data);
      break;
    case 'order.shipped':
      handleOrderShipped(data);
      break;
    case 'payment.processed':
      handlePaymentProcessed(data);
      break;
    default:
      console.log(`Unknown event type: ${eventType}`);
  }

  // Acknowledge receipt
  res.status(200).json({ success: true });
});

function verifySignature(payload, signature, timestamp) {
  const message = `${timestamp}.${JSON.stringify(payload)}`;
  const expectedSignature = crypto
    .createHmac('sha256', WEBHOOK_SECRET)
    .update(message)
    .digest('hex');

  return signature === expectedSignature;
}

function handleOrderCreated(data) {
  console.log(`Order ${data.orderId} created by customer ${data.customerId}`);
  // Update your database, send confirmation email, etc.
}

function handleOrderShipped(data) {
  console.log(`Order ${data.orderId} shipped with tracking ${data.trackingNumber}`);
  // Send shipment notification, update tracking, etc.
}

function handlePaymentProcessed(data) {
  console.log(`Payment ${data.paymentId} processed for order ${data.orderId}`);
  // Update order status, send receipt, etc.
}

app.listen(3000, () => console.log('Webhook receiver listening on port 3000'));
```

### Python Example

```python
from flask import Flask, request
import hmac
import hashlib
import json
from datetime import datetime

app = Flask(__name__)

WEBHOOK_SECRET = 'whsec_test_secret_key'

@app.route('/webhooks/ecommerce', methods=['POST'])
def webhook():
    # Verify signature
    signature = request.headers.get('X-Webhook-Signature')
    timestamp = request.headers.get('X-Webhook-Timestamp')

    if not verify_signature(request.get_data(), signature, timestamp):
        return {'error': 'Unauthorized'}, 401

    data = request.json
    event_type = data['eventType']
    event_data = data['data']

    print(f"Received webhook: {event_type}")

    # Handle different event types
    if event_type == 'order.created':
        handle_order_created(event_data)
    elif event_type == 'order.shipped':
        handle_order_shipped(event_data)
    elif event_type == 'payment.processed':
        handle_payment_processed(event_data)

    # Acknowledge receipt
    return {'success': True}, 200

def verify_signature(payload, signature, timestamp):
    message = f"{timestamp}.{payload.decode()}"
    expected_signature = hmac.new(
        WEBHOOK_SECRET.encode(),
        message.encode(),
        hashlib.sha256
    ).hexdigest()

    return hmac.compare_digest(signature, expected_signature)

def handle_order_created(data):
    print(f"Order {data['orderId']} created")
    # Update database, send confirmation email, etc.

def handle_order_shipped(data):
    print(f"Order {data['orderId']} shipped")
    # Send shipment notification, update tracking, etc.

def handle_payment_processed(data):
    print(f"Payment {data['paymentId']} processed")
    # Update order status, send receipt, etc.

if __name__ == '__main__':
    app.run(port=3000, debug=True)
```

## Security

### Signature Verification

All webhooks include a signature header for verification:

```
X-Webhook-Signature: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6
X-Webhook-Timestamp: 1705317000
```

The signature is created using HMAC-SHA256:

```
signature = HMAC-SHA256(secret, timestamp + "." + body)
```

### Best Practices

1. **Always verify signatures** - Protect against replay attacks and ensure authenticity
2. **Use HTTPS** - Only register HTTPS webhook URLs
3. **Implement timeouts** - Your webhook must respond within 30 seconds
4. **Handle retries** - Don't duplicate actions for the same `eventId`
5. **Log webhooks** - Keep audit trail of all received events
6. **Use idempotency** - Same event should produce same result if processed multiple times

## Retry Policy

When webhook delivery fails, the system automatically retries with the following schedule:

| Attempt | Delay | Total Wait |
|---------|-------|-----------|
| 1st attempt | - | 0 seconds |
| 2nd attempt | 1 minute | 1 minute |
| 3rd attempt | 5 minutes | 6 minutes |
| 4th attempt | 30 minutes | 36 minutes |
| 5th attempt | 2 hours | 2 hours 36 minutes |

After 5 failed attempts, the webhook is disabled. You'll receive an email notification.

### Re-enable Failed Webhooks

```bash
curl -X PATCH https://api.ecommerce.local/api/webhooks/wh_1234567890 \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"active": true}'
```

## Managing Webhooks

### List Webhooks

```bash
curl https://api.ecommerce.local/api/webhooks \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Update Webhook

```bash
curl -X PATCH https://api.ecommerce.local/api/webhooks/wh_1234567890 \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "events": [
      "order.created",
      "order.cancelled"
    ],
    "active": true
  }'
```

### Delete Webhook

```bash
curl -X DELETE https://api.ecommerce.local/api/webhooks/wh_1234567890 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Webhook Delivery History

```bash
curl https://api.ecommerce.local/api/webhooks/wh_1234567890/deliveries \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## Testing Webhooks

### Using Webhook.cool

Use [webhook.cool](https://webhook.cool) for testing:

1. Go to webhook.cool and get a unique URL
2. Register that URL with the E-Commerce API
3. Trigger an event and view the delivered payload
4. Verify signature and structure

### Using ngrok

Expose your local webhook receiver to the internet:

```bash
ngrok http 3000
# Get forwarding URL (e.g., https://abc123.ngrok.io)

# Register webhook:
curl -X POST https://api.ecommerce.local/api/webhooks \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://abc123.ngrok.io/webhooks/ecommerce",
    "events": ["order.created"],
    "active": true
  }'

# Test by creating an order - webhook will be delivered
```

## Troubleshooting

### Webhook Not Received

1. Verify webhook URL is accessible and responds with 200 OK
2. Check webhook is marked as `active`
3. Review retry history in webhook details
4. Enable detailed logging on your endpoint
5. Check firewall rules allow inbound requests from API servers

### Signature Verification Failing

1. Verify `WEBHOOK_SECRET` matches registered secret
2. Ensure timestamp is within acceptable window (±5 minutes)
3. Check message format: `{timestamp}.{body}`
4. Verify HMAC-SHA256 implementation
5. Log raw payload for debugging

### Duplicate Processing

1. Store `eventId` and check if already processed
2. Implement database-level uniqueness constraints
3. Use transactions to ensure exactly-once processing

### High Failure Rates

1. Check webhook endpoint performance and latency
2. Verify SSL/TLS certificate validity
3. Review error logs for patterns
4. Increase timeout if processing takes time
5. Check network connectivity

## Example Event Payloads

### order.created

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "order.created",
  "timestamp": "2024-01-15T10:30:00Z",
  "data": {
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
    "createdAt": "2024-01-15T10:30:00Z"
  }
}
```

### order.shipped

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440001",
  "eventType": "order.shipped",
  "timestamp": "2024-01-15T14:20:00Z",
  "data": {
    "orderId": 1001,
    "customerId": 5,
    "trackingNumber": "1Z999AA10123456784",
    "carrier": "UPS",
    "estimatedDeliveryDate": "2024-01-18"
  }
}
```

### payment.processed

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440002",
  "eventType": "payment.processed",
  "timestamp": "2024-01-15T10:31:00Z",
  "data": {
    "paymentId": 5001,
    "orderId": 1001,
    "customerId": 5,
    "amount": 299.99,
    "currency": "USD",
    "transactionId": "txn_1234567890",
    "processedAt": "2024-01-15T10:31:00Z"
  }
}
```

## Support

For webhook issues:

- **Email**: webhooks-support@ecommerce.local
- **Slack**: #webhooks-support
- **Portal**: https://ecommerce.local/support/webhooks
