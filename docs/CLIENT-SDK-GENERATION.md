# Client SDK Generation Guide

This document describes how to generate client SDKs for the E-Commerce Microservices API in multiple programming languages.

## Overview

Client SDKs are automatically generated from the OpenAPI 3.0 specification (`openapi-spec/openapi-unified.yaml`) using industry-standard code generators.

## Supported Languages

- **Java** - Modern synchronous and asynchronous clients
- **Python** - Pythonic API clients with type hints
- **TypeScript/JavaScript** - Full-featured frontend and Node.js clients

## Prerequisites

### Global Tools

```bash
# Install OpenAPI Generator
brew install openapi-generator  # macOS
# or
npm install -g @openapitools/openapi-generator-cli  # npm

# Install for Java (optional, if not using npm)
npm install -g @openapitools/openapi-generator-cli
```

### Java Generation

```bash
# Using Maven plugin (recommended)
# Maven 3.6.0+ is required
mvn openapi-generator:generate

# Using OpenAPI Generator CLI
openapi-generator-cli generate \
  -i openapi-spec/openapi-unified.yaml \
  -g java \
  -o generated-sdks/java
```

### Python Generation

```bash
# Ensure you have the generator installed
pip install openapi-generator-cli

# Generate Python client
openapi-generator generate \
  -i openapi-spec/openapi-unified.yaml \
  -g python \
  -o generated-sdks/python
```

### TypeScript/JavaScript Generation

```bash
# Using npm
npm install -g openapi-generator-cli

# Generate TypeScript client
openapi-generator-cli generate \
  -i openapi-spec/openapi-unified.yaml \
  -g typescript-axios \
  -o generated-sdks/typescript
```

## Maven Plugin Configuration

Add this to your parent `pom.xml` to auto-generate SDKs:

```xml
<plugin>
  <groupId>org.openapitools</groupId>
  <artifactId>openapi-generator-maven-plugin</artifactId>
  <version>6.4.0</version>
  <executions>
    <execution>
      <id>java-client</id>
      <phase>generate-sources</phase>
      <goals>
        <goal>generate</goal>
      </goals>
      <configuration>
        <inputSpec>${project.basedir}/openapi-spec/openapi-unified.yaml</inputSpec>
        <generatorName>java</generatorName>
        <output>${project.basedir}/generated-sdks/java</output>
        <apiPackage>com.ecommerce.api.client</apiPackage>
        <modelPackage>com.ecommerce.api.model</modelPackage>
        <configOptions>
          <dateLibrary>java8</dateLibrary>
          <java8>true</java8>
          <useRuntimeException>true</useRuntimeException>
          <library>restclient</library>
        </configOptions>
      </configuration>
    </execution>
  </executions>
</plugin>
```

## Java SDK Usage

### Installation

Add to your `pom.xml`:

```xml
<dependency>
  <groupId>com.ecommerce</groupId>
  <artifactId>ecommerce-api-client</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Example Usage

```java
import com.ecommerce.api.client.*;
import com.ecommerce.api.model.*;

public class EcommerceExample {
  public static void main(String[] args) {
    // Configure API client
    ApiClient defaultClient = Configuration.getDefaultApiClient();
    defaultClient.setBasePath("http://localhost:8080");

    // Set Bearer token
    HttpBearerAuth bearer = (HttpBearerAuth) defaultClient.getAuthentication("BearerAuth");
    bearer.setBearerToken("YOUR_TOKEN");

    // Create customer API instance
    CustomerApi customerApi = new CustomerApi(defaultClient);

    try {
      // Get all customers
      PagedResponse customers = customerApi.getAll(0, 20, "id");
      System.out.println("Retrieved customers: " + customers.getTotalElements());

      // Create customer
      CreateCustomerRequest request = new CreateCustomerRequest();
      request.setEmail("customer@example.com");
      request.setFirstName("John");
      request.setLastName("Doe");

      CustomerResponse createdCustomer = customerApi.create(request);
      System.out.println("Created customer with ID: " + createdCustomer.getId());

      // Get customer by ID
      CustomerResponse customer = customerApi.getById(createdCustomer.getId());
      System.out.println("Customer: " + customer.getEmail());

      // Update customer
      CreateCustomerRequest updateRequest = new CreateCustomerRequest();
      updateRequest.setEmail("updated@example.com");
      updateRequest.setFirstName("Jane");
      updateRequest.setLastName("Doe");

      CustomerResponse updatedCustomer = customerApi.update(
        createdCustomer.getId(),
        updateRequest
      );
      System.out.println("Updated customer: " + updatedCustomer.getEmail());

      // Delete customer
      customerApi.delete(createdCustomer.getId());
      System.out.println("Customer deleted");

    } catch (ApiException e) {
      System.err.println("Exception when calling CustomerApi: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
```

### Async/Await Pattern

```java
public class AsyncExample {
  public static void main(String[] args) {
    ApiClient defaultClient = Configuration.getDefaultApiClient();
    defaultClient.setBasePath("http://localhost:8080");

    HttpBearerAuth bearer = (HttpBearerAuth) defaultClient.getAuthentication("BearerAuth");
    bearer.setBearerToken("YOUR_TOKEN");

    CustomerApi customerApi = new CustomerApi(defaultClient);

    // Async call with callback
    customerApi.getAllAsync(0, 20, "id", new ApiCallback<PagedResponse>() {
      @Override
      public void onSuccess(PagedResponse result, int statusCode, Map<String, List<String>> responseHeaders) {
        System.out.println("Customers retrieved: " + result.getTotalElements());
      }

      @Override
      public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
        System.err.println("Failed to retrieve customers: " + e.getMessage());
      }

      @Override
      public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {}

      @Override
      public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {}
    });
  }
}
```

## Python SDK Usage

### Installation

```bash
pip install ecommerce-api-client
```

Or from source:

```bash
cd generated-sdks/python
pip install -e .
```

### Example Usage

```python
import time
from ecommerce_api_client import ApiClient, Configuration
from ecommerce_api_client.api import CustomerApi
from ecommerce_api_client.model.create_customer_request import CreateCustomerRequest

# Configure API client
config = Configuration()
config.host = "http://localhost:8080"
config.access_token = "YOUR_TOKEN"

api_client = ApiClient(config)
customer_api = CustomerApi(api_client)

try:
    # Get all customers
    customers = customer_api.get_all(page=0, size=20)
    print(f"Retrieved {customers.total_elements} customers")

    # Create customer
    new_customer = CreateCustomerRequest(
        email="customer@example.com",
        first_name="John",
        last_name="Doe",
        phone_number="+1-555-0123"
    )
    created = customer_api.create(new_customer)
    print(f"Created customer with ID: {created.id}")

    # Get customer by ID
    customer = customer_api.get_by_id(created.id)
    print(f"Customer: {customer.email}")

    # Update customer
    update_data = CreateCustomerRequest(
        email="updated@example.com",
        first_name="Jane",
        last_name="Doe"
    )
    updated = customer_api.update(created.id, update_data)
    print(f"Updated customer: {updated.email}")

    # Delete customer
    customer_api.delete(created.id)
    print("Customer deleted")

except Exception as e:
    print(f"Exception when calling CustomerApi: {e}")
```

### Async Pattern (asyncio)

```python
import asyncio
from ecommerce_api_client import ApiClient, Configuration
from ecommerce_api_client.api import CustomerApi

async def main():
    config = Configuration()
    config.host = "http://localhost:8080"
    config.access_token = "YOUR_TOKEN"

    api_client = ApiClient(config)
    customer_api = CustomerApi(api_client)

    # Async operations
    customers = await customer_api.get_all_async(page=0, size=20)
    print(f"Retrieved {customers.total_elements} customers")

asyncio.run(main())
```

## TypeScript/JavaScript SDK Usage

### Installation

```bash
npm install ecommerce-api-client axios
```

### Example Usage

```typescript
import { Configuration, ApiClient, CustomerApi } from 'ecommerce-api-client';

const config = new Configuration({
  basePath: 'http://localhost:8080',
  accessToken: 'YOUR_TOKEN'
});

const apiClient = new ApiClient(config);
const customerApi = new CustomerApi(apiClient);

async function example() {
  try {
    // Get all customers
    const customers = await customerApi.getAll(0, 20);
    console.log(`Retrieved ${customers.totalElements} customers`);

    // Create customer
    const newCustomer = {
      email: 'customer@example.com',
      firstName: 'John',
      lastName: 'Doe',
      phoneNumber: '+1-555-0123'
    };
    const created = await customerApi.create(newCustomer);
    console.log(`Created customer with ID: ${created.id}`);

    // Get customer by ID
    const customer = await customerApi.getById(created.id);
    console.log(`Customer: ${customer.email}`);

    // Update customer
    const updateData = {
      email: 'updated@example.com',
      firstName: 'Jane',
      lastName: 'Doe'
    };
    const updated = await customerApi.update(created.id, updateData);
    console.log(`Updated customer: ${updated.email}`);

    // Delete customer
    await customerApi.delete(created.id);
    console.log('Customer deleted');

  } catch (error) {
    console.error('Exception when calling CustomerApi:', error);
  }
}

example();
```

### React Usage

```tsx
import { useEffect, useState } from 'react';
import { ApiClient, Configuration, CustomerApi } from 'ecommerce-api-client';

export function CustomerList() {
  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const config = new Configuration({
      basePath: 'http://localhost:8080',
      accessToken: localStorage.getItem('authToken') || ''
    });

    const apiClient = new ApiClient(config);
    const customerApi = new CustomerApi(apiClient);

    customerApi.getAll(0, 20)
      .then(response => {
        setCustomers(response.content);
      })
      .catch(error => {
        console.error('Failed to fetch customers:', error);
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  if (loading) return <div>Loading...</div>;

  return (
    <div>
      <h1>Customers</h1>
      <ul>
        {customers.map(customer => (
          <li key={customer.id}>
            {customer.firstName} {customer.lastName} ({customer.email})
          </li>
        ))}
      </ul>
    </div>
  );
}
```

## SDK Configuration Options

### Common Configuration Options

For all SDK generators, you can customize:

```bash
openapi-generator-cli generate \
  -i openapi-spec/openapi-unified.yaml \
  -g java \
  -o generated-sdks/java \
  -c sdk-config.yaml
```

### sdk-config.yaml

```yaml
packageName: com.ecommerce.api.client
packageVersion: 1.0.0
projectName: ecommerce-api-client

# HTTP Library (Java)
library: restclient  # or: jersey2, okhttp-gson, retrofit2, google-api-client

# Date Format
dateLibrary: java8  # or: joda, java8, jsr310

# Additional options
java8: true
useRuntimeException: true
skipValidation: false

# Model/API packages
apiPackage: com.ecommerce.api.client
modelPackage: com.ecommerce.api.model
invokerPackage: com.ecommerce.api.invoker
```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Generate SDKs

on:
  push:
    paths:
      - 'openapi-spec/**'

jobs:
  generate-sdks:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Setup Node
        uses: actions/setup-node@v3
        with:
          node-version: '18'

      - name: Install generator
        run: npm install -g @openapitools/openapi-generator-cli

      - name: Generate Java SDK
        run: |
          openapi-generator-cli generate \
            -i openapi-spec/openapi-unified.yaml \
            -g java \
            -o generated-sdks/java

      - name: Generate Python SDK
        run: |
          openapi-generator-cli generate \
            -i openapi-spec/openapi-unified.yaml \
            -g python \
            -o generated-sdks/python

      - name: Generate TypeScript SDK
        run: |
          openapi-generator-cli generate \
            -i openapi-spec/openapi-unified.yaml \
            -g typescript-axios \
            -o generated-sdks/typescript

      - name: Commit changes
        run: |
          git config --local user.email "action@github.com"
          git config --local user.name "SDK Generator"
          git add generated-sdks/
          git commit -m "Auto-generated SDKs from OpenAPI spec"
          git push
```

## Publishing SDKs

### Maven Central (Java)

```xml
<distributionManagement>
  <snapshotRepository>
    <id>ossrh</id>
    <url>https://s01.oss.sonatype.org/content/repositories/snapshots</url>
  </snapshotRepository>
  <repository>
    <id>ossrh</id>
    <url>https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/</url>
  </repository>
</distributionManagement>
```

### PyPI (Python)

```bash
cd generated-sdks/python
python setup.py sdist bdist_wheel
twine upload dist/*
```

### NPM (JavaScript/TypeScript)

```bash
cd generated-sdks/typescript
npm publish
```

## Troubleshooting

### Generator Issues

**Issue: "Invalid OpenAPI specification"**
- Validate spec: `openapi-generator-cli validate -i openapi-spec/openapi-unified.yaml`

**Issue: "Unknown generator"**
- List available generators: `openapi-generator-cli list`

**Issue: "Generation incomplete"**
- Check generator version compatibility
- Update generator: `npm install -g @openapitools/openapi-generator-cli@latest`

## Best Practices

1. **Version SDKs** - Use semantic versioning (major.minor.patch)
2. **Publish regularly** - Regenerate and publish SDKs with API changes
3. **Add documentation** - Include README and examples in generated SDKs
4. **Test generation** - Validate generated code before releasing
5. **Customize templates** - Use custom templates for specific requirements
6. **Document API changes** - Keep CHANGELOG.md updated

## Additional Resources

- [OpenAPI Generator Documentation](https://openapi-generator.tech/)
- [OpenAPI Specification](https://spec.openapis.org/oas/v3.0.0)
- [SDK Best Practices](https://swagger.io/blog/generating-sdks-with-openapi-generator/)
