# Product Service Documentation

## Overview

The Product Service is a microservice responsible for managing the product catalog in the e-commerce platform. It provides REST APIs for CRUD operations on products, handles product categories, manages inventory tracking, and publishes events to other services.

**Service Name**: product-service  
**Port**: 8085  
**Base URL**: `http://localhost:8085`

## Features

- **Product Management**: Create, read, update, and delete products
- **Product Catalog**: Browse products with pagination and filtering
- **Category Management**: Organize products by categories
- **Inventory Tracking**: Monitor product availability and quantity
- **Event-Driven Architecture**: Publishes and consumes Kafka events
- **Multi-Database Support**: H2, PostgreSQL, Oracle
- **RESTful API**: Comprehensive REST endpoints
- **Validation**: Input validation with detailed error messages
- **Caching**: Redis-based caching for improved performance

## Database Schema

### Products Table
```sql
CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description CLOB,
    price DECIMAL(19, 2) NOT NULL,
    sku VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(100) NOT NULL,
    quantity_available INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

### Product Categories Table
```sql
CREATE TABLE product_categories (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

## REST API Endpoints

### Create Product
**Endpoint**: `POST /api/products`

**Request Body**:
```json
{
    "name": "Wireless Headphones",
    "description": "High-quality Bluetooth headphones",
    "price": 79.99,
    "sku": "SKU-001",
    "category": "Electronics",
    "quantityAvailable": 50
}
```

**Response** (201 Created):
```json
{
    "id": 1,
    "name": "Wireless Headphones",
    "description": "High-quality Bluetooth headphones",
    "price": 79.99,
    "sku": "SKU-001",
    "category": "Electronics",
    "quantityAvailable": 50,
    "createdAt": "2024-01-15T10:00:00",
    "updatedAt": "2024-01-15T10:00:00"
}
```

### Get Product by ID
**Endpoint**: `GET /api/products/{id}`

**Response** (200 OK):
```json
{
    "id": 1,
    "name": "Wireless Headphones",
    "description": "High-quality Bluetooth headphones",
    "price": 79.99,
    "sku": "SKU-001",
    "category": "Electronics",
    "quantityAvailable": 50,
    "createdAt": "2024-01-15T10:00:00",
    "updatedAt": "2024-01-15T10:00:00"
}
```

### Get Product by SKU
**Endpoint**: `GET /api/products/sku/{sku}`

**Example**: `GET /api/products/sku/SKU-001`

**Response** (200 OK):
```json
{
    "id": 1,
    "name": "Wireless Headphones",
    "price": 79.99,
    "sku": "SKU-001",
    "category": "Electronics",
    "quantityAvailable": 50
}
```

### List All Products
**Endpoint**: `GET /api/products`

**Query Parameters**:
- `page` (default: 0) - Page number (0-indexed)
- `size` (default: 20) - Page size
- `sort` - Sort criteria (e.g., `name,asc` or `price,desc`)

**Example**: `GET /api/products?page=0&size=10&sort=name,asc`

**Response** (200 OK):
```json
{
    "content": [
        {
            "id": 1,
            "name": "Wireless Headphones",
            "price": 79.99,
            "sku": "SKU-001",
            "category": "Electronics",
            "quantityAvailable": 50
        }
    ],
    "pageable": {
        "pageNumber": 0,
        "pageSize": 10,
        "sort": []
    },
    "totalElements": 1,
    "totalPages": 1
}
```

### Get Products by Category
**Endpoint**: `GET /api/products/category/{category}`

**Example**: `GET /api/products/category/Electronics?page=0&size=10`

**Response** (200 OK):
```json
{
    "content": [
        {
            "id": 1,
            "name": "Wireless Headphones",
            "price": 79.99,
            "sku": "SKU-001",
            "category": "Electronics",
            "quantityAvailable": 50
        }
    ],
    "totalElements": 1,
    "totalPages": 1
}
```

### Search Products
**Endpoint**: `GET /api/products/search`

**Query Parameters**:
- `term` (required) - Search term for product name

**Example**: `GET /api/products/search?term=headphones&page=0&size=10`

**Response** (200 OK):
```json
{
    "content": [
        {
            "id": 1,
            "name": "Wireless Headphones",
            "price": 79.99,
            "sku": "SKU-001",
            "category": "Electronics",
            "quantityAvailable": 50
        }
    ],
    "totalElements": 1,
    "totalPages": 1
}
```

### Get Available Products
**Endpoint**: `GET /api/products/available`

Returns products with quantityAvailable > 0

**Example**: `GET /api/products/available?page=0&size=10`

**Response** (200 OK):
```json
{
    "content": [
        {
            "id": 1,
            "name": "Wireless Headphones",
            "price": 79.99,
            "sku": "SKU-001",
            "category": "Electronics",
            "quantityAvailable": 50
        }
    ],
    "totalElements": 1,
    "totalPages": 1
}
```

### Get Low Stock Products
**Endpoint**: `GET /api/products/low-stock`

Returns products with quantityAvailable <= 10

**Response** (200 OK):
```json
[
    {
        "id": 2,
        "name": "USB-C Cable",
        "price": 12.99,
        "sku": "SKU-002",
        "category": "Electronics",
        "quantityAvailable": 5
    }
]
```

### Update Product
**Endpoint**: `PUT /api/products/{id}`

**Request Body** (all fields optional):
```json
{
    "name": "Updated Product Name",
    "description": "Updated description",
    "price": 89.99,
    "category": "Electronics",
    "quantityAvailable": 75
}
```

**Response** (200 OK):
```json
{
    "id": 1,
    "name": "Updated Product Name",
    "price": 89.99,
    "sku": "SKU-001",
    "category": "Electronics",
    "quantityAvailable": 75,
    "updatedAt": "2024-01-15T11:30:00"
}
```

### Delete Product
**Endpoint**: `DELETE /api/products/{id}`

**Response** (204 No Content)

### Reserve Inventory
**Endpoint**: `POST /api/products/{id}/reserve`

**Query Parameters**:
- `quantity` (required) - Quantity to reserve

**Example**: `POST /api/products/1/reserve?quantity=10`

**Response** (200 OK)

### Release Inventory
**Endpoint**: `POST /api/products/{id}/release`

**Query Parameters**:
- `quantity` (required) - Quantity to release

**Example**: `POST /api/products/1/release?quantity=10`

**Response** (200 OK)

## Kafka Events

### Published Events

#### ProductCreatedEvent
Published when a new product is created
```json
{
    "productId": 1,
    "name": "Wireless Headphones",
    "sku": "SKU-001",
    "price": 79.99,
    "category": "Electronics",
    "quantityAvailable": 50,
    "eventTime": "2024-01-15T10:00:00"
}
```

**Topic**: `product-events`

#### ProductUpdatedEvent
Published when a product is updated
```json
{
    "productId": 1,
    "name": "Updated Headphones",
    "price": 89.99,
    "category": "Electronics",
    "quantityAvailable": 45,
    "eventTime": "2024-01-15T11:00:00"
}
```

**Topic**: `product-events`

#### ProductDeletedEvent
Published when a product is deleted
```json
{
    "productId": 1,
    "sku": "SKU-001",
    "eventTime": "2024-01-15T12:00:00"
}
```

**Topic**: `product-events`

### Subscribed Events

#### InventoryReservedEvent
Received from inventory service when inventory is reserved
```json
{
    "productId": 1,
    "quantityReserved": 10,
    "eventTime": "2024-01-15T13:00:00"
}
```

**Topic**: `inventory-events`

#### InventoryReleasedEvent
Received from inventory service when inventory is released
```json
{
    "productId": 1,
    "quantityReleased": 10,
    "eventTime": "2024-01-15T14:00:00"
}
```

**Topic**: `inventory-events`

## Error Responses

### 404 Not Found
```json
{
    "timestamp": "2024-01-15T10:00:00",
    "status": 404,
    "error": "Not Found",
    "message": "Product with id 999 not found",
    "path": "/api/products/999"
}
```

### 409 Conflict (Duplicate SKU)
```json
{
    "timestamp": "2024-01-15T10:00:00",
    "status": 409,
    "error": "Conflict",
    "message": "Product with SKU SKU-001 already exists",
    "path": "/api/products"
}
```

### 400 Bad Request (Validation Error)
```json
{
    "timestamp": "2024-01-15T10:00:00",
    "status": 400,
    "error": "Bad Request",
    "message": "Validation failed",
    "validationErrors": {
        "name": "Product name is required",
        "price": "Product price must be greater than 0"
    },
    "path": "/api/products"
}
```

## Running the Service

### With H2 (In-Memory Database)
```bash
cd services/product-service
mvn spring-boot:run
```

### With PostgreSQL
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=postgres"
```

### With Oracle
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=oracle"
```

### Running Tests
```bash
mvn test
```

### Running Integration Tests
```bash
mvn verify
```

## Configuration

### Environment Variables
- `SPRING_PROFILES_ACTIVE` - Active profile (h2, postgres, oracle)
- `KAFKA_BOOTSTRAP_SERVERS` - Kafka broker addresses
- `SPRING_DATASOURCE_URL` - Database URL
- `SPRING_DATASOURCE_USERNAME` - Database username
- `SPRING_DATASOURCE_PASSWORD` - Database password

### Application Properties
See `application.yml` and profile-specific files for configuration options.

## Service Dependencies

- **Kafka**: For event publishing and consumption
- **Redis**: For caching product data
- **Database**: H2, PostgreSQL, or Oracle
- **Eureka**: Service discovery

## Health Check

**Endpoint**: `GET /actuator/health`

**Response**:
```json
{
    "status": "UP",
    "components": {
        "db": {"status": "UP"},
        "kafkaProducer": {"status": "UP"},
        "redis": {"status": "UP"}
    }
}
```

## Metrics

**Endpoint**: `GET /actuator/metrics`

Available metrics include:
- HTTP request counts and latencies
- Database query metrics
- Kafka producer/consumer metrics
- JVM memory and CPU usage
