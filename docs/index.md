# E-Commerce Microservices API Documentation

Welcome to the official API documentation for the E-Commerce Microservices platform. This documentation provides comprehensive information about all available APIs, authentication mechanisms, rate limiting policies, and code examples.

## Overview

The E-Commerce Microservices platform provides a complete set of RESTful APIs for managing:

- **Customers** - Customer management and profiles
- **Orders** - Order creation, tracking, and management
- **Inventory** - Stock management and reservation
- **Payments** - Payment processing and refunds
- **Authentication** - Secure JWT-based authentication

## Key Features

✨ **Production-Ready APIs**
- RESTful design with standard HTTP methods
- Comprehensive error handling
- Rate limiting and quotas
- JWT Bearer token authentication

📚 **Well-Documented**
- OpenAPI 3.0 specification
- Interactive Swagger UI
- AsyncAPI for event-driven features
- Code examples in multiple languages

🔗 **Event-Driven Architecture**
- Apache Kafka integration
- Webhook support
- Event schemas and documentation
- Real-time event streaming

🛠️ **Developer-Friendly**
- Auto-generated client SDKs (Java, Python, TypeScript)
- Postman collection
- Comprehensive API examples
- Rate limiting documentation

## Quick Links

### For Getting Started
- [Authentication Guide](getting-started/authentication.md) - Learn how to authenticate
- [Quick Start](getting-started/quickstart.md) - 5-minute tutorial
- [API Examples](API-EXAMPLES.md) - Code samples in curl, JavaScript, Python

### For API Details
- [OpenAPI Specification](https://api.ecommerce.local/swagger-ui.html) - Interactive API docs
- [Customer API](api-reference/customers.md)
- [Order API](api-reference/orders.md)
- [Inventory API](api-reference/inventory.md)
- [Payment API](api-reference/payments.md)

### For Advanced Usage
- [Rate Limiting](RATE-LIMITING.md) - Quota and throttling policies
- [Webhooks](webhooks.md) - Real-time event notifications
- [Client SDKs](sdks/overview.md) - Generate client libraries
- [Event APIs](events.md) - Kafka topic documentation

## API Endpoints

### Base URLs

```
Development:  http://localhost:8080
Staging:      https://api.staging.ecommerce.local
Production:   https://api.ecommerce.local
```

### Core Endpoints

| Service | Base Path | Description |
|---------|-----------|-------------|
| Authentication | `/api/auth` | User login and authorization |
| Customers | `/api/customers` | Customer management |
| Orders | `/api/orders` | Order operations |
| Inventory | `/api/inventory` | Stock management |
| Payments | `/api/payments` | Payment processing |

## Authentication

All endpoints (except `/api/auth/login`) require Bearer token authentication:

```bash
Authorization: Bearer {JWT_TOKEN}
```

See [Authentication Guide](getting-started/authentication.md) for detailed instructions.

## Rate Limits

The API implements rate limiting to ensure fair usage:

- **Unauthenticated**: 10 req/min, 100 req/hour
- **Authenticated**: 60 req/min, 1000 req/hour
- **Premium**: 300 req/min, 5000 req/hour

See [Rate Limiting Documentation](RATE-LIMITING.md) for details.

## Pagination

List endpoints support pagination:

```bash
GET /api/customers?page=0&size=20&sortBy=id
```

Response includes pagination metadata:

```json
{
  "content": [...],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

## Error Handling

Errors follow a consistent format:

```json
{
  "errorCode": "RESOURCE_NOT_FOUND",
  "message": "Customer with ID 999 not found",
  "timestamp": "2024-01-15T10:30:00Z",
  "details": {}
}
```

See [Error Handling Guide](guides/error-handling.md) for error codes and solutions.

## Client Libraries

Generate SDKs for your preferred language:

- [Java SDK](sdks/java.md)
- [Python SDK](sdks/python.md)
- [TypeScript SDK](sdks/typescript.md)

See [SDK Generation Guide](CLIENT-SDK-GENERATION.md) for auto-generation instructions.

## Event APIs

Real-time events via Apache Kafka:

```
Topic: order-created
Event: Customer places a new order

Topic: payment-processed
Event: Payment successfully completed
```

See [Event APIs](events.md) and [AsyncAPI Spec](asyncapi-events.yaml) for all events.

## Support

### Getting Help

- **API Support**: api-support@ecommerce.local
- **Support Portal**: https://ecommerce.local/support
- **Slack**: #api-support channel
- **Status Page**: https://status.ecommerce.local

### Resources

- [Postman Collection](postman-collection.json) - Import into Postman
- [OpenAPI Spec](openapi-spec/openapi-unified.yaml) - Machine-readable spec
- [AsyncAPI Spec](openapi-spec/asyncapi-events.yaml) - Event documentation
- [Webhook Schema](docs/webhooks-schema.json) - Event payload schemas

## Changelog

### Version 1.0.0 (2024-01-15)

**Initial Release**
- Complete REST API for customers, orders, inventory, and payments
- JWT authentication with Bearer tokens
- Rate limiting per endpoint
- Webhook support for real-time events
- Event-driven architecture with Kafka
- Client SDK generation support (Java, Python, TypeScript)
- Comprehensive API documentation
- Postman collection

## Environment-Specific Information

### Development

- Base URL: `http://localhost:8080`
- Test credentials: username: `test_user`, password: `test_password`
- Rate limits: Relaxed for testing
- Data: Ephemeral (cleared daily)

### Staging

- Base URL: `https://api.staging.ecommerce.local`
- Reflects production configuration
- Real rate limiting enforced
- Persistent test data

### Production

- Base URL: `https://api.ecommerce.local`
- SLA: 99.9% uptime
- Rate limiting: Strict quotas enforced
- Data: Production data only

## Best Practices

1. **Use SDKs** - Generated client libraries handle authentication and serialization
2. **Implement retries** - Use exponential backoff for transient failures
3. **Monitor quotas** - Check rate limit headers in responses
4. **Cache responses** - Reduce API calls with intelligent caching
5. **Use pagination** - Always use page/size parameters for list endpoints
6. **Handle errors** - Implement proper error handling and logging
7. **Keep tokens secure** - Never commit tokens to version control
8. **Use HTTPS** - Always use encrypted connections in production

## Next Steps

1. **New to the API?** Start with the [Quick Start Guide](getting-started/quickstart.md)
2. **Ready to integrate?** Generate a [Client SDK](sdks/overview.md) for your language
3. **Building webhooks?** Read the [Webhook Guide](webhooks.md)
4. **Need code examples?** Check [API Examples](API-EXAMPLES.md)

---

Last updated: January 15, 2024
