# Rate Limiting Documentation

## Overview

The E-Commerce Microservices API implements rate limiting to protect services from excessive requests and ensure fair usage among all clients. Rate limits are applied at both the API Gateway and individual service levels.

## Rate Limit Tiers

### Standard Rate Limits

All API endpoints follow these default rate limits:

| Tier | Requests/Minute | Requests/Hour | Requests/Day |
|------|-----------------|---------------|--------------|
| Public (Unauthenticated) | 10 | 100 | 1,000 |
| Authenticated User | 60 | 1,000 | 10,000 |
| Premium User | 300 | 5,000 | 50,000 |
| API Key (Business) | 600 | 10,000 | 100,000 |
| Admin | Unlimited | Unlimited | Unlimited |

### Endpoint-Specific Limits

#### Authentication Service
```
# Login/brute-force protection now lives in Keycloak (realm brute-force detection, 5 failures -> lockout)
GET  /api/auth/me          : 120 requests/minute
```

#### Customer Service
```
GET    /api/customers              : 100 requests/minute
POST   /api/customers              : 20 requests/minute
GET    /api/customers/{id}         : 100 requests/minute
PUT    /api/customers/{id}         : 20 requests/minute
DELETE /api/customers/{id}         : 5 requests/minute
```

#### Order Service
```
GET    /api/orders                 : 100 requests/minute
POST   /api/orders                 : 30 requests/minute
GET    /api/orders/{id}            : 100 requests/minute
PUT    /api/orders/{id}/status     : 20 requests/minute
```

#### Inventory Service
```
GET    /api/inventory              : 120 requests/minute
POST   /api/inventory              : 30 requests/minute
GET    /api/inventory/{id}         : 120 requests/minute
PUT    /api/inventory/{id}         : 30 requests/minute
POST   /api/inventory/{id}/reserve : 50 requests/minute
POST   /api/inventory/{id}/release : 50 requests/minute
```

#### Payment Service
```
GET    /api/payments               : 60 requests/minute
POST   /api/payments               : 10 requests/minute
GET    /api/payments/{id}          : 60 requests/minute
POST   /api/payments/{id}/refund   : 5 requests/minute
```

## Rate Limit Headers

All API responses include rate limit information in the response headers:

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 99
X-RateLimit-Reset: 1640000000
X-RateLimit-Retry-After: 60
```

### Header Definitions

| Header | Description |
|--------|-------------|
| `X-RateLimit-Limit` | Total requests allowed in the time window |
| `X-RateLimit-Remaining` | Number of requests remaining in current window |
| `X-RateLimit-Reset` | Unix timestamp when the current rate limit window resets |
| `X-RateLimit-Retry-After` | Seconds to wait before retrying (sent when limit exceeded) |

## HTTP Status Codes

### Rate Limit Exceeded (429 Too Many Requests)

When you exceed the rate limit, the API returns a `429 Too Many Requests` response:

```json
{
  "errorCode": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests. Please retry after 60 seconds",
  "timestamp": "2024-01-15T10:30:00Z",
  "details": {
    "limit": 100,
    "remaining": 0,
    "resetAt": 1705317000,
    "retryAfter": 60
  }
}
```

## Best Practices

### 1. Implement Exponential Backoff

```java
// Example: Retry with exponential backoff
int maxRetries = 3;
long initialBackoff = 1000; // milliseconds

for (int attempt = 0; attempt < maxRetries; attempt++) {
    try {
        response = apiClient.getCustomers();
        break;
    } catch (RateLimitException e) {
        long backoff = initialBackoff * (1L << attempt);
        Thread.sleep(backoff);
    }
}
```

### 2. Monitor Rate Limit Headers

```javascript
// Example: JavaScript
fetch('/api/customers')
  .then(response => {
    const remaining = response.headers.get('X-RateLimit-Remaining');
    const limit = response.headers.get('X-RateLimit-Limit');
    
    console.log(`Requests remaining: ${remaining}/${limit}`);
    
    if (remaining < 10) {
      console.warn('Approaching rate limit');
    }
    
    return response.json();
  });
```

### 3. Use Pagination to Reduce Requests

Instead of requesting all items:
```
GET /api/orders?page=0&size=20  // Good
GET /api/orders?page=1&size=20  // Good
GET /api/orders?page=2&size=20  // Good
```

### 4. Cache Responses When Possible

Cache frequently requested data with appropriate TTL values based on your business requirements.

### 5. Batch Operations

For bulk operations, use bulk endpoints when available instead of individual requests:
```
POST /api/inventory/batch-update  // Good (1 request)
vs.
PUT /api/inventory/1, PUT /api/inventory/2, etc.  // Bad (multiple requests)
```

## Quota Management

### Request Quota Reset

- **Per-minute quota**: Resets every 60 seconds
- **Per-hour quota**: Resets every 3600 seconds
- **Per-day quota**: Resets at midnight UTC

### Checking Your Quota

```bash
curl -i -H "Authorization: Bearer YOUR_TOKEN" \
  https://api.ecommerce.local/api/customers
```

Response headers will show:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1640000060
```

## Whitelisting and Exceptions

### Request Whitelisting

If you need higher rate limits for legitimate use cases:

1. Contact the API support team
2. Provide business justification
3. Specify the endpoints and required limits
4. Submit IP addresses or API keys to whitelist

### Whitelisted IPs

Whitelisted clients may have higher rate limits:
- Dedicated servers (higher quota by default)
- Specific partner integrations
- Internal services

## Rate Limit Recovery

### After Exceeding Limits

1. Check the `X-RateLimit-Reset` header for when your quota resets
2. Check the `X-RateLimit-Retry-After` header for recommended wait time
3. Implement exponential backoff in your retry logic
4. Consider upgrading to a higher tier if you consistently hit limits

### Gradual Recovery

Rate limits are applied per-client, per-endpoint:
- Each endpoint has independent rate limit counters
- Hitting limit on one endpoint doesn't affect others
- Quotas gradually recover as the time window progresses

## Monitoring and Alerts

### Set Up Alerts

Monitor your rate limit usage:

```javascript
// Alert when approaching limits
if (rateLimitRemaining < (rateLimitTotal * 0.1)) {
  sendAlert('Approaching rate limit');
}
```

### Metrics to Track

- Current rate limit usage
- Peak usage times
- Burst patterns
- Failed requests due to rate limiting
- Quota reset frequencies

## Troubleshooting

### Common Issues

**Issue: Receiving 429 Too Many Requests**
- Solution: Implement exponential backoff in retry logic
- Solution: Reduce request frequency
- Solution: Request rate limit increase from support

**Issue: Quota resets unpredictably**
- Solution: Check server timezone (UTC-based)
- Solution: Account for request processing time

**Issue: Rate limits differ per endpoint**
- Solution: This is intentional - endpoints have different criticality levels
- Solution: Refer to endpoint-specific limits table above

## FAQ

**Q: Can I request higher rate limits?**
A: Yes, contact api-support@ecommerce.local with your use case and requirements.

**Q: Do all team members share the same quota?**
A: No, each API key/token has its own rate limit quota.

**Q: What happens when I exceed daily limits?**
A: Daily limits reset at midnight UTC. Requests will be rejected until reset.

**Q: Are rate limits applied per IP or per API key?**
A: Per API key/authentication token.

**Q: Do cached responses count against rate limits?**
A: No, cached responses from our CDN do not count against rate limits.

## Support

For rate limiting issues or requests:
- Email: api-support@ecommerce.local
- Support Portal: https://ecommerce.local/support
- Slack: #api-support
