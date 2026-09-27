# E-Commerce Microservices API Documentation

Comprehensive API documentation for the E-Commerce Microservices platform.

## Documentation Structure

```
docs/
├── index.md                          # Main documentation homepage
├── README.md                         # This file
├── API-EXAMPLES.md                   # Code examples (curl, JS, Python)
├── RATE-LIMITING.md                  # Rate limiting policies and quotas
├── CLIENT-SDK-GENERATION.md          # Guide for generating client SDKs
├── webhooks.md                       # Webhook integration guide
├── mkdocs.yml                        # MkDocs configuration
├── webhooks-schema.json              # JSON schema for webhook payloads
├── getting-started/
│   ├── overview.md                   # API overview and architecture
│   ├── authentication.md             # Authentication guide (JWT)
│   └── quickstart.md                 # 5-minute quick start
├── api-reference/
│   ├── authentication.md             # Authentication endpoints
│   ├── customers.md                  # Customer API reference
│   ├── orders.md                     # Order API reference
│   ├── inventory.md                  # Inventory API reference
│   └── payments.md                   # Payment API reference
├── guides/
│   ├── error-handling.md             # Error codes and handling
│   └── best-practices.md             # Best practices guide
├── sdks/
│   ├── overview.md                   # SDK overview
│   ├── java.md                       # Java SDK usage
│   ├── python.md                     # Python SDK usage
│   └── typescript.md                 # TypeScript SDK usage
└── stylesheets/
    └── extra.css                     # Custom CSS styling
```

## Quick Start

### View Documentation Locally

```bash
# Install MkDocs and material theme
pip install mkdocs mkdocs-material

# Serve documentation locally
mkdocs serve

# Visit http://localhost:8000
```

### Build Static Documentation

```bash
mkdocs build
# Output is in ./site/ directory
```

## Key Sections

### 🚀 Getting Started
- [Overview](getting-started/overview.md) - Architecture and key concepts
- [Authentication](getting-started/authentication.md) - JWT token setup
- [Quick Start](getting-started/quickstart.md) - 5-minute tutorial

### 📚 API Reference
Complete endpoint documentation:
- [Authentication API](api-reference/authentication.md)
- [Customers API](api-reference/customers.md)
- [Orders API](api-reference/orders.md)
- [Inventory API](api-reference/inventory.md)
- [Payments API](api-reference/payments.md)

### 📖 Guides
- [Rate Limiting](RATE-LIMITING.md) - Quotas and throttling
- [Webhooks](webhooks.md) - Real-time events
- [Error Handling](guides/error-handling.md) - Error codes
- [Best Practices](guides/best-practices.md) - Integration tips

### 🔧 SDKs
Auto-generate client libraries:
- [SDK Overview](sdks/overview.md)
- [Java SDK](sdks/java.md)
- [Python SDK](sdks/python.md)
- [TypeScript SDK](sdks/typescript.md)
- [Generation Guide](CLIENT-SDK-GENERATION.md)

### 💻 Code Examples
[API Examples](API-EXAMPLES.md) with:
- cURL commands
- JavaScript
- Python
- Java

### 📤 Events
- [Event APIs](../openapi-spec/asyncapi-events.yaml) - Kafka topics
- [Webhooks](webhooks.md) - Real-time notifications

## File Descriptions

### Main Documentation Files

| File | Purpose |
|------|---------|
| `index.md` | Documentation home page |
| `API-EXAMPLES.md` | Code samples in multiple languages |
| `RATE-LIMITING.md` | Rate limit policies and quotas |
| `CLIENT-SDK-GENERATION.md` | Guide for SDK generation |
| `webhooks.md` | Webhook integration documentation |
| `webhooks-schema.json` | JSON schema for webhook events |

### Getting Started Guides

| File | Content |
|------|---------|
| `getting-started/overview.md` | Architecture overview |
| `getting-started/authentication.md` | JWT authentication setup |
| `getting-started/quickstart.md` | 5-minute quick start |

### API Reference

| File | Covers |
|------|--------|
| `api-reference/authentication.md` | Login, token refresh |
| `api-reference/customers.md` | Customer CRUD operations |
| `api-reference/orders.md` | Order management |
| `api-reference/inventory.md` | Stock management |
| `api-reference/payments.md` | Payment processing |

### Guides

| File | Topic |
|------|-------|
| `guides/error-handling.md` | Error codes and solutions |
| `guides/best-practices.md` | Integration best practices |

### SDK Documentation

| File | SDK |
|------|-----|
| `sdks/overview.md` | SDK overview |
| `sdks/java.md` | Java SDK usage |
| `sdks/python.md` | Python SDK usage |
| `sdks/typescript.md` | TypeScript SDK usage |

## Building and Deploying

### Local Development

```bash
pip install mkdocs mkdocs-material mkdocs-swagger-ui-tag

mkdocs serve
# Open http://localhost:8000
```

### Build Static Site

```bash
mkdocs build
# Creates ./site/ directory with static HTML
```

### Docker Deployment

```dockerfile
FROM python:3.11-slim

WORKDIR /docs

COPY requirements.txt .
RUN pip install -r requirements.txt

COPY . .

EXPOSE 8000
CMD ["mkdocs", "serve", "--dev-addr=0.0.0.0:8000"]
```

Build and run:
```bash
docker build -t ecommerce-docs .
docker run -p 8000:8000 ecommerce-docs
```

### CI/CD Integration

GitHub Actions example:

```yaml
name: Deploy Documentation

on:
  push:
    branches: [main, development]
    paths: ['docs/**', 'openapi-spec/**']

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - uses: actions/setup-python@v4
        with:
          python-version: '3.11'

      - name: Install dependencies
        run: pip install mkdocs mkdocs-material

      - name: Build site
        run: mkdocs build

      - name: Deploy to GitHub Pages
        uses: peaceiris/actions-gh-pages@v3
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: ./site
```

## Content Guidelines

### Writing Documentation

1. **Use clear headings** - Structure with H1, H2, H3
2. **Include examples** - Code snippets for complex topics
3. **Add links** - Reference related documentation
4. **Use admonitions** - For warnings, notes, tips
5. **Keep it concise** - Avoid overly long paragraphs
6. **Update regularly** - Keep examples current

### Markdown Format

```markdown
# Main Heading (H1)

## Section Heading (H2)

### Subsection (H3)

Code example:
\`\`\`bash
curl -X GET https://api.ecommerce.local/api/customers
\`\`\`

> **Note:** Important information

- Bullet list
- Another item
```

### Code Examples

Always include:
- cURL/HTTP examples
- At least one language example (JS, Python, or Java)
- Error handling
- Complete working examples

## Asset Management

### Organizing Assets

```
docs/
├── images/
│   ├── architecture.png
│   └── workflow.png
├── schemas/
│   ├── customer-schema.json
│   └── order-schema.json
└── downloads/
    └── postman-collection.json
```

### Adding Images

```markdown
![Image description](images/architecture.png)
```

## Search Functionality

MkDocs includes built-in search:
- Full-text search across all pages
- Keyboard shortcut: `Ctrl+K` (Windows/Linux) or `Cmd+K` (Mac)
- Indexed on build time

## Customization

### MkDocs Configuration

Edit `mkdocs.yml` to:
- Change site title/description
- Adjust theme colors
- Modify navigation structure
- Add plugins

### CSS Customization

Add custom styles to `stylesheets/extra.css`

### Theme Configuration

Material theme options in `mkdocs.yml`:
```yaml
theme:
  name: material
  palette:
    - scheme: light
    - scheme: dark
  features:
    - navigation.tabs
    - toc.integrate
```

## Validation and Testing

### Markdown Linting

```bash
npm install -g markdownlint-cli
markdownlint docs/
```

### Link Checking

```bash
brew install markdown-link-check
markdown-link-check docs/**/*.md
```

### Build Validation

```bash
mkdocs build --strict
```

## Version Control

### Documentation Changes

1. Update relevant `.md` files
2. Update specification files if API changed
3. Update examples if endpoints changed
4. Commit with clear message:
   ```
   docs: Add webhook integration guide
   ```

### Breaking Changes

When making breaking API changes:
1. Update API reference
2. Update examples
3. Add migration guide
4. Bump API version
5. Update CHANGELOG

## Support and Contribution

### Reporting Issues

Found an error or missing documentation?
- Open an issue in GitHub
- Email: docs@ecommerce.local
- Slack: #documentation

### Contributing Documentation

1. Fork the repository
2. Create feature branch: `git checkout -b docs/my-guide`
3. Make changes following guidelines
4. Test locally: `mkdocs serve`
5. Submit pull request

## Related Resources

- [OpenAPI Specifications](../openapi-spec/) - Machine-readable API specs
- [Postman Collection](../postman-collection.json) - Import into Postman
- [API Examples](API-EXAMPLES.md) - Code examples
- [Rate Limiting](RATE-LIMITING.md) - Quota information
- [Webhooks](webhooks.md) - Real-time events

## License

Documentation is licensed under Creative Commons Attribution 4.0 International (CC BY 4.0).

---

**Last Updated:** January 15, 2024
