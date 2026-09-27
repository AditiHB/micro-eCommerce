# OpenAPI Specifications

This directory contains all OpenAPI and AsyncAPI specifications for the E-Commerce Microservices platform.

## Files

### openapi-unified.yaml

Complete OpenAPI 3.0 specification that documents all REST API endpoints:

- **Authentication** - Login and authentication flows
- **Customers** - CRUD operations for customer management
- **Orders** - Order creation, tracking, and status management
- **Inventory** - Stock management, reservation, and release
- **Payments** - Payment processing and refunds

**Usage:**
- Import into Swagger UI: https://editor.swagger.io
- Generate client SDKs: `openapi-generator-cli generate -i openapi-unified.yaml -g <language>`
- Validate spec: `openapi-generator-cli validate -i openapi-unified.yaml`

### asyncapi-events.yaml

Complete AsyncAPI 2.6.0 specification for event-driven architecture:

- **Order Events** - order-created, order-confirmed, order-shipped, order-delivered
- **Inventory Events** - inventory-reserved, inventory-failed, inventory-released
- **Payment Events** - payment-processed, payment-failed, payment-refunded

**Usage:**
- View in AsyncAPI Studio: https://studio.asyncapi.com
- Document Kafka topics and event payloads
- Generate event-driven code

## Viewing Specifications

### Online Editors

1. **Swagger UI / OpenAPI Editor**
   ```
   https://editor.swagger.io
   ```
   - Copy-paste `openapi-unified.yaml` content
   - Interactive API documentation
   - Try it out feature for testing

2. **AsyncAPI Studio**
   ```
   https://studio.asyncapi.com
   ```
   - Copy-paste `asyncapi-events.yaml` content
   - Event schema visualization
   - Protocol details

### Local Viewing

**With Docker:**
```bash
# Swagger UI
docker run -p 8080:8080 -e SWAGGER_JSON=/specs/openapi-unified.yaml \
  -v $(pwd):/specs swaggerapi/swagger-ui

# AsyncAPI UI
docker run -p 8080:3000 \
  -e ASYNCAPI_SPEC=$(cat openapi-spec/asyncapi-events.yaml | base64) \
  asyncapi/studio
```

**With Node.js:**
```bash
# Serve specifications on local server
npm install -g http-server
http-server .
# Open http://localhost:8080 in browser
```

## Validation

### Validate OpenAPI Spec

```bash
# Using OpenAPI Generator
openapi-generator-cli validate -i openapi-unified.yaml

# Using Swagger CLI
swagger validate openapi-unified.yaml

# Using redoc-cli
redoc-cli validate openapi-unified.yaml
```

### Validate AsyncAPI Spec

```bash
# Using AsyncAPI CLI
npx @asyncapi/cli validate asyncapi-events.yaml
```

## Generating Documentation

### ReDoc Documentation

```bash
# Install redoc-cli
npm install -g redoc-cli

# Generate HTML documentation
redoc-cli bundle -o api-docs.html openapi-unified.yaml

# Serve documentation
redoc-cli serve openapi-unified.yaml
```

### MkDocs

```bash
# The main documentation site uses these specs
cd ../docs
mkdocs serve
# Visit http://localhost:8000
```

## Generating Client SDKs

The OpenAPI specification is used to auto-generate client libraries:

### Java
```bash
openapi-generator-cli generate \
  -i openapi-unified.yaml \
  -g java \
  -o ../generated-sdks/java \
  -c sdk-config.yaml
```

### Python
```bash
openapi-generator-cli generate \
  -i openapi-unified.yaml \
  -g python \
  -o ../generated-sdks/python \
  -c sdk-config.yaml
```

### TypeScript
```bash
openapi-generator-cli generate \
  -i openapi-unified.yaml \
  -g typescript-axios \
  -o ../generated-sdks/typescript \
  -c sdk-config.yaml
```

See [CLIENT-SDK-GENERATION.md](../docs/CLIENT-SDK-GENERATION.md) for details.

## Specification Structure

### OpenAPI Components

```yaml
components:
  schemas:        # Data models (request/response bodies)
  securitySchemes: # Authentication methods
  parameters:     # Reusable query parameters
  responses:      # Reusable response objects
  examples:       # Example payloads
```

### AsyncAPI Channels

```yaml
channels:
  channel-name:           # Topic/channel name
    subscribe:            # Events to subscribe to
    publish:              # Events to publish
    messages:             # Message definitions
```

## Best Practices

### When Updating Specifications

1. **Validate changes** - Use validation tools before committing
2. **Test generation** - Generate SDKs to verify no breaking changes
3. **Document breaking changes** - Update CHANGELOG if API changes
4. **Update examples** - Keep code examples in sync with specs
5. **Test new endpoints** - Verify new endpoints work before release
6. **Semantic versioning** - Bump version in info.version

### Specification Conventions

- Use consistent naming (camelCase for properties, PascalCase for models)
- Document all required fields
- Include examples in schemas
- Use meaningful descriptions
- Group related endpoints with tags
- Define error responses consistently

## Maintenance

### Version Control

- Keep specifications in sync with actual API implementation
- Review spec changes in pull requests
- Tag releases with API version

### CI/CD Integration

Validate specifications in your CI pipeline:

```yaml
# GitHub Actions example
- name: Validate OpenAPI Spec
  run: |
    npx @openapitools/openapi-generator-cli validate \
      -i openapi-spec/openapi-unified.yaml

- name: Validate AsyncAPI Spec
  run: |
    npx @asyncapi/cli validate openapi-spec/asyncapi-events.yaml
```

## Related Documentation

- [Rate Limiting](../docs/RATE-LIMITING.md) - API quotas and throttling
- [Webhooks](../docs/webhooks.md) - Real-time event notifications
- [API Examples](../docs/API-EXAMPLES.md) - Code samples
- [Client SDK Generation](../docs/CLIENT-SDK-GENERATION.md) - Auto-generate SDKs
- [API Documentation](../docs/index.md) - Main docs site

## Support

For specification issues:
- File issues in repository
- Email: api-spec@ecommerce.local
- Slack: #api-documentation
