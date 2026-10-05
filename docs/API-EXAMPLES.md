# API Usage Examples

Complete examples for using the E-Commerce Microservices API with curl, JavaScript, Python, and Java.

## Base URL

```
Development:  http://localhost:8080
Staging:      https://api.staging.ecommerce.local
Production:   https://api.ecommerce.local
```

## Authentication

All endpoints (except login) require a Bearer token in the Authorization header:

```bash
Authorization: Bearer {JWT_TOKEN}
```

## Authentication

There is **no login endpoint on this API**. Access tokens are issued by Keycloak (realm `ecommerce`); send the token
as `Authorization: Bearer <token>`. Roles, the access matrix and the object-level rules are in
[KEYCLOAK_IDENTITY.md](KEYCLOAK_IDENTITY.md). Credentials are never written in this repo: source your git-ignored `.env`
(`scripts/gen-env.sh`) first.

### Get an access token (development: password grant on the `ecommerce-e2e` client)

#### cURL
```bash
set -a; . ./.env; set +a
curl -s http://localhost:8180/realms/ecommerce/protocol/openid-connect/token   -d grant_type=password -d client_id=ecommerce-e2e -d "client_secret=$E2E_CLIENT_SECRET"   -d username=karate_admin -d "password=$E2E_ADMIN_PASSWORD"
```

#### Response (abridged)
```json
{ "access_token": "eyJhbGciOiJSUzI1NiIs...", "expires_in": 300, "token_type": "Bearer", "refresh_token": "..." }
```
The token is an RS256 JWT with `iss`, `aud: ["ecommerce-api", ...]`, `realm_access.roles`, and, for customers, `customer_id`.

#### JavaScript (browser apps)
Browsers must **not** use the password grant. Use the authorization-code flow with PKCE against the public
`ecommerce-web` client (a library such as `oidc-client-ts` or `keycloak-js` does this):
```javascript
import Keycloak from 'keycloak-js';
const keycloak = new Keycloak({ url: 'http://localhost:8180', realm: 'ecommerce', clientId: 'ecommerce-web' });
await keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256' });
const res = await fetch('http://localhost:8080/api/orders', { headers: { Authorization: `Bearer ${keycloak.token}` } });
```

#### Python
```python
import os, requests
tok = requests.post('http://localhost:8180/realms/ecommerce/protocol/openid-connect/token', data={
    'grant_type': 'password', 'client_id': 'ecommerce-e2e', 'client_secret': os.environ['E2E_CLIENT_SECRET'],
    'username': 'karate_admin', 'password': os.environ['E2E_ADMIN_PASSWORD']}).json()['access_token']
orders = requests.get('http://localhost:8080/api/orders', headers={'Authorization': f'Bearer {tok}'}).json()
```

> The remaining examples below were written for the earlier self-issued-token design. Wherever they show a
> login call or a `token` from `/api/auth/login`, substitute a Keycloak access token as above; endpoint paths and
> payloads are unchanged, but some operations now need a back-office role (see the access matrix).

## Customer API

### List Customers

#### cURL
```bash
curl -X GET "http://localhost:8080/api/customers?page=0&size=20" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### With Sorting
```bash
curl -X GET "http://localhost:8080/api/customers?page=0&size=20&sortBy=firstName" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### JavaScript
```javascript
const getCustomers = async (token, page = 0, size = 20) => {
  const response = await fetch(
    `/api/customers?page=${page}&size=${size}`,
    {
      headers: { 'Authorization': `Bearer ${token}` }
    }
  );
  return response.json();
};

const customers = await getCustomers(token);
```

#### Python
```python
import requests

headers = {'Authorization': f'Bearer {token}'}
response = requests.get(
    'http://localhost:8080/api/customers',
    params={'page': 0, 'size': 20},
    headers=headers
)
customers = response.json()
```

### Get Customer by ID

```bash
curl -X GET http://localhost:8080/api/customers/1 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### Response
```json
{
  "id": 1,
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+1-555-0123",
  "address": "123 Main St, Anytown, USA",
  "city": "Anytown",
  "country": "USA",
  "postalCode": "12345",
  "createdAt": "2024-01-10T10:30:00Z",
  "updatedAt": "2024-01-15T14:20:00Z"
}
```

### Create Customer

#### cURL
```bash
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "email": "jane@example.com",
    "firstName": "Jane",
    "lastName": "Smith",
    "phoneNumber": "+1-555-0456",
    "address": "456 Oak Ave, Somewhere, USA",
    "city": "Somewhere",
    "country": "USA",
    "postalCode": "54321"
  }'
```

#### JavaScript
```javascript
const createCustomer = async (token, customerData) => {
  const response = await fetch('/api/customers', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify(customerData)
  });
  return response.json();
};

const newCustomer = await createCustomer(token, {
  email: 'jane@example.com',
  firstName: 'Jane',
  lastName: 'Smith',
  phoneNumber: '+1-555-0456'
});
```

#### Python
```python
import requests

headers = {'Authorization': f'Bearer {token}'}
data = {
    'email': 'jane@example.com',
    'firstName': 'Jane',
    'lastName': 'Smith',
    'phoneNumber': '+1-555-0456'
}
response = requests.post(
    'http://localhost:8080/api/customers',
    json=data,
    headers=headers
)
new_customer = response.json()
```

### Update Customer

```bash
curl -X PUT http://localhost:8080/api/customers/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "email": "john.updated@example.com",
    "firstName": "John",
    "lastName": "Doe Updated",
    "phoneNumber": "+1-555-9999"
  }'
```

### Delete Customer

```bash
curl -X DELETE http://localhost:8080/api/customers/1 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## Order API

### List Orders

```bash
curl -X GET "http://localhost:8080/api/orders?page=0&size=10" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Create Order

#### cURL
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "customerId": 1,
    "items": [
      {
        "productId": 1,
        "quantity": 2
      },
      {
        "productId": 2,
        "quantity": 1
      }
    ],
    "shippingAddress": "123 Main St, Anytown, USA"
  }'
```

#### Response
```json
{
  "id": 1001,
  "customerId": 1,
  "status": "PENDING",
  "items": [
    {
      "productId": 1,
      "quantity": 2,
      "unitPrice": 149.99,
      "subtotal": 299.98
    }
  ],
  "totalAmount": 299.98,
  "shippingAddress": "123 Main St, Anytown, USA",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

#### JavaScript
```javascript
const createOrder = async (token, orderData) => {
  const response = await fetch('/api/orders', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify(orderData)
  });
  
  if (!response.ok) {
    throw new Error(`Order creation failed: ${response.status}`);
  }
  
  return response.json();
};

const order = await createOrder(token, {
  customerId: 1,
  items: [
    { productId: 1, quantity: 2 },
    { productId: 2, quantity: 1 }
  ],
  shippingAddress: '123 Main St, Anytown, USA'
});
```

#### Python
```python
import requests

headers = {'Authorization': f'Bearer {token}'}
order_data = {
    'customerId': 1,
    'items': [
        {'productId': 1, 'quantity': 2},
        {'productId': 2, 'quantity': 1}
    ],
    'shippingAddress': '123 Main St, Anytown, USA'
}
response = requests.post(
    'http://localhost:8080/api/orders',
    json=order_data,
    headers=headers
)
order = response.json()
```

### Update Order Status

```bash
curl -X PUT "http://localhost:8080/api/orders/1/status?status=CONFIRMED" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

Status values: `PENDING`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `FAILED`

## Inventory API

### List Inventory

```bash
curl -X GET "http://localhost:8080/api/inventory?page=0&size=20" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Get Inventory by ID

```bash
curl -X GET http://localhost:8080/api/inventory/1 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Create Inventory

```bash
curl -X POST http://localhost:8080/api/inventory \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "productId": 1,
    "quantity": 100,
    "reorderLevel": 20
  }'
```

### Reserve Stock

```bash
curl -X POST "http://localhost:8080/api/inventory/1/reserve?quantity=5" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Release Stock

```bash
curl -X POST "http://localhost:8080/api/inventory/1/release?quantity=5" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Update Inventory Quantity

```bash
curl -X PUT "http://localhost:8080/api/inventory/1?quantity=150" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## Payment API

### List Payments

```bash
curl -X GET "http://localhost:8080/api/payments?page=0&size=20" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Process Payment

#### cURL
```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "orderId": 1001,
    "amount": 299.99,
    "currency": "USD",
    "paymentMethod": "CREDIT_CARD",
    "cardToken": "tok_visa"
  }'
```

#### Response
```json
{
  "id": 5001,
  "orderId": 1001,
  "amount": 299.99,
  "currency": "USD",
  "status": "COMPLETED",
  "paymentMethod": "CREDIT_CARD",
  "transactionId": "txn_1234567890",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

#### JavaScript
```javascript
const processPayment = async (token, paymentData) => {
  const response = await fetch('/api/payments', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify(paymentData)
  });
  return response.json();
};

const payment = await processPayment(token, {
  orderId: 1001,
  amount: 299.99,
  currency: 'USD',
  paymentMethod: 'CREDIT_CARD',
  cardToken: 'tok_visa'
});
```

### Refund Payment

```bash
curl -X POST http://localhost:8080/api/payments/5001/refund \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## Error Handling

### Common Error Responses

#### 400 Bad Request
```json
{
  "errorCode": "VALIDATION_FAILED",
  "message": "Validation failed",
  "timestamp": "2024-01-15T10:30:00Z",
  "details": {
    "field": "email",
    "message": "Email must be valid"
  }
}
```

#### 401 Unauthorized
```json
{
  "errorCode": "UNAUTHORIZED",
  "message": "Authentication required",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

#### 404 Not Found
```json
{
  "errorCode": "RESOURCE_NOT_FOUND",
  "message": "Customer with ID 999 not found",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

#### 429 Too Many Requests
```json
{
  "errorCode": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests",
  "timestamp": "2024-01-15T10:30:00Z",
  "details": {
    "limit": 100,
    "remaining": 0,
    "resetAt": 1705317000
  }
}
```

## Pagination

All list endpoints support pagination:

```bash
curl -X GET "http://localhost:8080/api/customers?page=0&size=20&sortBy=id" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

Response includes:
```json
{
  "content": [...],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

## Best Practices

1. **Always handle errors**: Check response status codes and error details
2. **Use pagination**: Don't request all records at once
3. **Cache when appropriate**: Use ETags and conditional requests
4. **Monitor rate limits**: Track X-RateLimit-Remaining header
5. **Implement retries**: Use exponential backoff for transient errors
6. **Validate input**: Ensure all required fields are provided
7. **Use HTTPS**: Always use HTTPS in production
8. **Store tokens securely**: Never expose tokens in logs or version control

## Complete End-to-End Example

```javascript
async function ecommerceExample() {
  const apiBase = 'http://localhost:8080';
  
  try {
    // 1. Login
    const loginRes = await fetch(`${apiBase}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: 'john_doe',
        password: 'securePassword123'
      })
    });
    const { token } = await loginRes.json();
    
    // 2. Create customer
    const customerRes = await fetch(`${apiBase}/api/customers`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({
        email: 'customer@example.com',
        firstName: 'John',
        lastName: 'Doe'
      })
    });
    const customer = await customerRes.json();
    
    // 3. Create order
    const orderRes = await fetch(`${apiBase}/api/orders`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({
        customerId: customer.id,
        items: [{ productId: 1, quantity: 2 }],
        shippingAddress: '123 Main St'
      })
    });
    const order = await orderRes.json();
    
    // 4. Process payment
    const paymentRes = await fetch(`${apiBase}/api/payments`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({
        orderId: order.id,
        amount: order.totalAmount,
        paymentMethod: 'CREDIT_CARD'
      })
    });
    const payment = await paymentRes.json();
    
    console.log('Order completed successfully!', {
      customer: customer.id,
      order: order.id,
      payment: payment.id
    });
    
  } catch (error) {
    console.error('Error:', error);
  }
}
```
