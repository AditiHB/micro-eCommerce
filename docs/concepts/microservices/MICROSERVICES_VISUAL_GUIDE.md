# Microservices Visual Guide 📊
## Understanding YOUR Architecture Through Diagrams

---

## 🎯 Complete System Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          CLIENT APPLICATIONS                             │
│                  (Web, Mobile, 3rd Party Integrations)                  │
└───────────────────────────┬──────────────────────────────────────────────┘
                            │
                            │ HTTP/HTTPS
                            ▼
        ┌───────────────────────────────────┐
        │      🚪 API GATEWAY               │
        │      (Port 8080)                  │
        │                                   │
        │  ├─ Routes requests               │
        │  ├─ Validates JWT tokens          │
        │  ├─ Rate limiting (100 req/min)  │
        │  ├─ Load balancing                │
        │  └─ CORS handling                 │
        └────────────────┬────────────────┘
                         │
         ┌───────────────┼───────────────┬──────────────┬──────────────┐
         │               │               │              │              │
         ▼               ▼               ▼              ▼              ▼
    ┌─────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
    │Customer │  │  Order   │  │ Payment  │  │Inventory │  │ Product  │
    │Service  │  │ Service  │  │ Service  │  │ Service  │  │ Service  │
    │:8081    │  │ :8083    │  │ :8084    │  │ :8082    │  │ :8085    │
    └────┬────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘
         │            │             │             │             │
         ▼            ▼             ▼             ▼             ▼
    ┌─────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
    │Customer │  │  Order   │  │ Payment  │  │Inventory │  │ Product  │
    │DB (PG)  │  │ DB (PG)  │  │ DB (PG)  │  │DB(Mongo) │  │ DB (PG)  │
    └─────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘
         △            △             △             △             △
         └────────────┴─────────────┴─────────────┴─────────────┘
                                    │
                    Async communication via Kafka
                           (Event Bus)
                                    ▼
        ┌──────────────────────────────────────────┐
        │  📬 KAFKA MESSAGE BROKER                │
        │  (Port 9092 - External)                  │
        │  (Port 29092 - Internal)                 │
        │                                          │
        │  Topics:                                 │
        │  ├─ order.created                       │
        │  ├─ payment.processed                   │
        │  ├─ payment.failed                      │
        │  ├─ inventory.reserved                  │
        │  ├─ inventory.failed                    │
        │  ├─ order.shipped                       │
        │  └─ order.delivered                     │
        └──────────────────────────────────────────┘
                        △
                        │
        ┌───────────────┴────────────────┐
        │                                │
    ┌───────────────────┐    ┌───────────────────┐
    │  💾 PERSISTENCE   │    │ 🔍 OBSERVABILITY  │
    │  SERVICES         │    │ SERVICES          │
    │                   │    │                   │
    │ ├─ Redis (cache)  │    │ ├─ Prometheus     │
    │ └─ S3 (files)     │    │ ├─ Grafana        │
    │                   │    │ ├─ Elasticsearch  │
    │                   │    │ ├─ Logstash       │
    │                   │    │ └─ Kibana (logs)  │
    └───────────────────┘    └───────────────────┘
        
    ┌────────────────────────────────────────┐
    │  ⚙️  INFRASTRUCTURE SERVICES           │
    │                                        │
    │  ├─ 🔍 Eureka Discovery Server :8761  │
    │  │   (Service Registry)                │
    │  │                                     │
    │  ├─ ⚙️  Config Server :8888            │
    │  │   (Centralized Configuration)       │
    │  │                                     │
    │  └─ 📊 Admin Dashboard                 │
    │     (Monitoring & Control)             │
    └────────────────────────────────────────┘
```

---

## 🔄 Service Communication Pattern

### **How Services Talk to Each Other**

```
PATTERN 1: SYNCHRONOUS (Direct Call)
═════════════════════════════════════

Customer Service needs to validate customer exists:

    API Gateway
        │
        ▼
    Customer Controller
        │
        ▼
    Create Order
        │
        ├─ Need to validate customer?
        │  │
        │  └─→ Call REST API:
        │      GET /api/customers/5
        │      │
        │      └─→ Eureka finds "customer-service" URL
        │          │
        │          └─→ HTTP call to customer-service:8081
        │              │
        │              └─→ WAIT HERE (blocking)
        │                  Customer Service processes
        │                  │
        │                  └─→ Response: 200 OK, Customer data
        │                      │
        │                      └─→ Continue in Order Service

Timeline: ←────── Blocking Wait ──────→
          Start call          Get response
          (0ms)              (50-500ms)

⚠️  Problem: If Customer Service is slow, Order Service is slow too!
```

```
PATTERN 2: ASYNCHRONOUS (Event-Driven)
═══════════════════════════════════════

Order created → Publish event, don't wait for payment/inventory:

    Order Service
        │
        ├─ Create order in database (FAST: 10ms)
        │
        ├─ Publish "OrderCreatedEvent" to Kafka (FAST: 5ms)
        │  │
        │  └─→ Kafka stores event in topic "order.created"
        │       │
        │       └─→ RETURN IMMEDIATELY (Order created!)
        │           Don't wait for payment/inventory
        │
        └─→ Return to client (< 50ms total) ✅


Meanwhile, in the background (asynchronously):
─────────────────────────────────────────────

Payment Service (subscribes to "order.created"):
    ├─ Receives OrderCreatedEvent
    ├─ Processes payment (might take 2-3 seconds)
    ├─ Publishes "PaymentProcessedEvent" or "PaymentFailedEvent"
    └─ Done


Inventory Service (subscribes to "order.created"):
    ├─ Receives OrderCreatedEvent
    ├─ Reserves stock (might take 100ms)
    ├─ Publishes "InventoryReservedEvent" or "InventoryFailedEvent"
    └─ Done


Timeline:
┌─────────────────────────────────────────────────────┐
│ 0ms      Order Service returns to client ← FAST!   │
│ 100ms    Inventory completes reservation             │
│ 2500ms   Payment completes                           │
│ Total client wait: 50ms, not 2500ms! 🚀             │
└─────────────────────────────────────────────────────┘

✅ Benefit: Services work in parallel, not waiting for each other!
```

---

## 🏃 Complete Order Flow Visualization

```
CUSTOMER PLACES AN ORDER - DETAILED FLOW
═════════════════════════════════════════

Timeline: T0 to T5

                            ╔════════════════════════════════════════════╗
                            ║        YOUR APPLICATION (Browser)         ║
                            ║  POST /api/orders                          ║
                            ║  {                                         ║
                            ║    "customerId": 5,                       ║
                            ║    "items": [                              ║
                            ║      {"productId": 10, "quantity": 2}     ║
                            ║    ]                                       ║
                            ║  }                                         ║
                            ╚═════════════┬══════════════════════════════╝
                                          │
                                          ▼
                            ╔════════════════════════════════════════════╗
                            ║       API GATEWAY (Port 8080)              ║
                            ║                                             ║
T0: ──────────────          ║  1. Validate JWT token        ✅           ║
    START (0ms)             ║  2. Check rate limit          ✅           ║
                            ║  3. Find Order Service route:              ║
                            ║     "POST /api/orders" → order-service    ║
                            ║  4. Forward request to :8083               ║
                            ╚═════════════┬══════════════════════════════╝
                                          │
                                          ▼
                            ╔════════════════════════════════════════════╗
                            ║    ORDER SERVICE (Port 8083)               ║
                            ║                                             ║
T1: ──────────────          ║  1. Receive POST /api/orders               ║
    (5-10ms)                ║  2. Create Order object:                   ║
                            ║     {id:123, status:"CREATED", ...}       ║
                            ║  3. Save to order_db (PostgreSQL)          ║
                            ║     INSERT INTO orders (...)               ║
                            ║     Execution: 10ms                        ║
                            ║  4. Create OrderCreatedEvent               ║
                            ║     {orderId:123, customerId:5, ...}       ║
                            ║  5. Publish to Kafka (fire & forget)       ║
                            ║     kafkaTemplate.send("order.created",... ║
                            ║  6. Return response to client:             ║
                            ║     {                                      ║
                            ║       "orderId": 123,                      ║
                            ║       "status": "CREATED"                  ║
                            ║     }                                       ║
                            ║  7. RETURN IMMEDIATELY                     ║
                            ╚═════════════╤══════════════════════════════╝
                                          │
                    [ORDER CREATED - Returned to client in ~40ms total! ✅]
                                          │
                    ┌─────────────────────┴──────────────────────┐
                    │                                            │
                    ▼ (ASYNC)                               ▼ (ASYNC)
        
    ╔════════════════════════════════════╗  ╔════════════════════════════════════╗
    ║  PAYMENT SERVICE (Port 8084)       ║  ║  INVENTORY SERVICE (Port 8082)    ║
    ║                                    ║  ║                                    ║
T2: ─ (Starts ~T1, runs parallel) ─     ║  ║  1. Listens to "order.created" ║
    ║                                    ║  ║     Receives same OrderCreatedEvent║
    ║  1. Listens to Kafka topic         ║  ║  2. Extract order details         ║
    ║     "order.created"                ║  ║  3. Query MongoDB:                ║
    ║  2. Receives OrderCreatedEvent     ║  ║     SELECT product WHERE id=10    ║
    ║  3. Extract payment info:          ║  ║     Current stock: 50             ║
    ║     orderId: 123                   ║  ║  4. Check if enough stock:        ║
    ║     customerId: 5                  ║  ║     Need: 2                       ║
    ║     amount: $100                   ║  ║     Have: 50                      ║
    ║  4. Call payment gateway:          ║  ║     YES! ✅                       ║
    ║     stripeClient.charge(...)       ║  ║  5. Reserve stock:                ║
    ║                                    ║  ║     INSERT INTO reservations (...) ║
T3: ║  5. Stripe processing             ║  ║  6. Update product:               ║
    ║     (takes 1-2 seconds)            ║  ║     UPDATE products               ║
    ║     ✅ SUCCESS!                    ║  ║     SET available_qty = 48,       ║
    ║                                    ║  ║         reserved_qty = 2          ║
    ║  6. Save payment record:           ║  ║  7. Publish event:                ║
    ║     INSERT INTO payments (...)     ║  ║     "InventoryReservedEvent"      ║
    ║     status: COMPLETED              ║  ║  8. DONE (took ~100ms)            ║
    ║  7. Publish to Kafka:              ║  ║                                    ║
    ║     "payment.processed"            ║  ╚════════════════════════════════════╝
    ║  8. DONE (took ~2 seconds)         ║
    ║                                    ║
    ╚════════════════════════════════════╝

┌─────────────────────────────────────────────────────────────┐
│  T4: Kafka has 2 events                                     │
│  ├─ payment.processed ✅                                   │
│  └─ inventory.reserved ✅                                  │
│                                                             │
│  Order Service listens to both:                            │
│  ├─ Payment successful ✅                                  │
│  └─ Inventory reserved ✅                                  │
│                                                             │
│  Updates Order #123 in order_db:                           │
│  status = PAID + STOCK_RESERVED                            │
│                                                             │
│  T5: COMPLETE ✅                                           │
│      Order successfully processed!                          │
│      Total time: ~2.5 seconds (parallel processing)         │
│      (If synchronous, would take: 50ms + 100ms + 2000ms)   │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔄 SAGA Pattern - Distributed Transaction

```
SAGA PATTERN: Handling Failures Across Services
═══════════════════════════════════════════════

SCENARIO: What if payment fails?
─────────────────────────────────

Step 1: Order Created (SAGA starts)
───────────────────────────────────
    OrderService executes:
    ├─ Create Order
    │ └─ INSERT INTO orders VALUES(123, ...)
    ├─ Publish: OrderCreatedEvent
    │ └─ Kafka: order.created
    │
    └─ ORDER CREATED ✅


Step 2: Inventory Reserve (Saga Step 1)
────────────────────────────────────────
    InventoryService listens to "order.created":
    ├─ Check stock available
    │ └─ Product#10 has 50 units
    ├─ Reserve stock
    │ └─ INSERT INTO reservations VALUES(orderId=123, qty=2, status='RESERVED')
    │    UPDATE products SET available_qty = 48, reserved_qty = 2
    ├─ Publish: InventoryReservedEvent ✅
    │
    └─ INVENTORY RESERVED ✅


Step 3: Payment Process (Saga Step 2)
────────────────────────────────────────
    PaymentService listens to "order.created":
    ├─ Charge credit card: $100
    │ └─ Stripe API call
    │    ✋ CARD DECLINED! ❌
    │
    ├─ Publish: PaymentFailedEvent ❌
    │
    └─ PAYMENT FAILED ❌


Step 4: COMPENSATION BEGINS (Saga Rollback)
─────────────────────────────────────────────

    InventoryService listens to "payment.failed":
    ├─ Receive: PaymentFailedEvent(orderId=123)
    ├─ ROLLBACK inventory reserve:
    │ └─ DELETE FROM reservations WHERE orderId = 123
    │    UPDATE products SET available_qty = 50, reserved_qty = 0
    │
    └─ INVENTORY RELEASED ✅


    OrderService listens to "payment.failed":
    ├─ Receive: PaymentFailedEvent(orderId=123)
    ├─ ROLLBACK order:
    │ └─ UPDATE orders SET status = 'FAILED' WHERE id = 123
    │
    └─ ORDER CANCELLED ✅


RESULT:
───────
✅ Stock returned to inventory (50 units back)
✅ Customer NOT charged
✅ Order marked as FAILED
✅ Either EVERYTHING succeeds OR EVERYTHING rolls back!

This is the SAGA pattern: Compensating Transactions


Visual Timeline:
────────────────

T0: OrderCreated
    └─→ Status: CREATED
        Stock: 48
        Payment: -

T1: InventoryReserved (Saga Step 1)
    └─→ Status: CREATED
        Stock: 48 ✅ (reserved)
        Payment: -

T2: PaymentFailed (Saga Step 2 FAILS)
    └─→ Trigger compensations

T3: InventoryReleased (Compensation)
    └─→ Status: CREATED
        Stock: 50 ✅ (released)
        Payment: -

T4: OrderCancelled (Compensation)
    └─→ Status: FAILED ✅
        Stock: 50 ✅
        Payment: ✅ NOT charged

Total result: ALL rolled back successfully!
```

---

## 🔍 Service Discovery with Eureka

```
SERVICE DISCOVERY FLOW
═════════════════════

┌─────────────────────────────────────────────────────────────────┐
│                  EUREKA DISCOVERY SERVER                        │
│                    (Port 8761)                                  │
│                                                                 │
│  Service Registry:                                             │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Service Name       │ Instance URL        │ Status       │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ CUSTOMER-SERVICE   │ 192.168.1.2:8081    │ ⭐ UP        │   │
│  │ ORDER-SERVICE      │ 192.168.1.3:8083    │ ⭐ UP        │   │
│  │ PAYMENT-SERVICE    │ 192.168.1.4:8084    │ ⭐ UP        │   │
│  │ INVENTORY-SERVICE  │ 192.168.1.5:8082    │ ⭐ UP        │   │
│  │ PRODUCT-SERVICE    │ 192.168.1.6:8085    │ ⭐ UP        │   │
│  │ CONFIG-SERVER      │ 192.168.1.7:8888    │ ⭐ UP        │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Every 30 seconds: Health check each service                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘


REGISTRATION FLOW (Service Startup)
───────────────────────────────────

T0: Order Service starts
    │
    ├─ Main Spring Boot application initialized
    │
    ├─ Reads: application.yml
    │  eureka:
    │    client:
    │      register-with-eureka: true
    │      serviceUrl:
    │        defaultZone: http://discovery-server:8761/eureka/
    │
    ├─ Contacts Eureka: "Hi! I'm ORDER-SERVICE"
    │  POST http://discovery-server:8761/eureka/apps/ORDER-SERVICE
    │  Body:
    │  {
    │    "instance": {
    │      "hostName": "order-service",
    │      "app": "ORDER-SERVICE",
    │      "ipAddr": "192.168.1.3",
    │      "port": 8083,
    │      "status": "UP",
    │      "healthCheckUrl": "http://192.168.1.3:8083/actuator/health"
    │    }
    │  }
    │
    ├─ Eureka responds: "Registered! ✅"
    │
    └─ Order Service now DISCOVERABLE


DISCOVERY FLOW (Service Lookup)
────────────────────────────────

T1: Payment Service needs to call Order Service
    │
    ├─ Payment code wants Order data:
    │  @FeignClient("ORDER-SERVICE")
    │  public interface OrderClient {
    │      @GetMapping("/orders/{id}")
    │      Order getOrder(@PathVariable Long id);
    │  }
    │
    ├─ FeignClient asks Eureka:
    │  "Where is ORDER-SERVICE?"
    │
    ├─ Eureka responds:
    │  "ORDER-SERVICE is at http://192.168.1.3:8083"
    │
    ├─ Payment Service connects:
    │  GET http://192.168.1.3:8083/orders/123
    │
    ├─ Order Service responds:
    │  {
    │    "id": 123,
    │    "status": "PAID",
    │    "amount": 100.00
    │  }
    │
    └─ Done! ✅


HEALTH CHECK & FAILURE DETECTION
─────────────────────────────────

Every 30 seconds, Eureka checks health:

T0:00   Order Service is healthy ✅
        Eureka: Status = UP

T0:30   Eureka: GET /actuator/health
        ✅ Response: {status: UP}
        Eureka: Status = UP

T1:00   Eureka: GET /actuator/health
        ✅ Response: {status: UP}
        Eureka: Status = UP

T1:30   Order Service crashes! ❌
        Eureka: GET /actuator/health
        ❌ NO RESPONSE (timeout)
        Eureka: Status = ???

T2:00   Still no response
        Failures: 2
        Eureka: Status = DOWN

T2:30   Still no response
        Failures: 3
        Eureka: REMOVES ORDER-SERVICE from registry!
        ⚠️  NOW: Other services know it's down


Payment Service tries to call Order:
        FeignClient asks: "Where is ORDER-SERVICE?"
        Eureka: "Not available"
        Payment returns graceful error:
        "Order Service temporarily unavailable"
        
Result: Other services NOT affected! 🎯


ORDER-SERVICE COMES BACK UP
─────────────────────────────

T5:00   Order Service starts again
        └─ Re-registers with Eureka
        └─ Eureka: Status = UP
        └─ Other services can find it again! ✅
```

---

## 📊 Message Flow Through Kafka

```
KAFKA EVENT FLOW - VISUAL
═════════════════════════

                    ┌─────────────────────────────┐
                    │   KAFKA BROKER              │
                    │   (Message Queue)           │
                    │                             │
                    │  Topics:                   │
                    │  ┌─────────────────────┐   │
                    │  │ order.created       │   │
                    │  │ ┌─────────────────┐ │   │
                    │  │ │ Message 1       │ │   │
                    │  │ ├─────────────────┤ │   │
                    │  │ │ orderId: 123    │ │   │
                    │  │ │ customerId: 5   │ │   │
                    │  │ │ amount: $100    │ │   │
                    │  │ └─────────────────┘ │   │
                    │  └─────────────────────┘   │
                    │                             │
                    │  ┌─────────────────────┐   │
                    │  │ payment.processed   │   │
                    │  │ ┌─────────────────┐ │   │
                    │  │ │ (empty now)     │ │   │
                    │  │ └─────────────────┘ │   │
                    │  └─────────────────────┘   │
                    │                             │
                    │  ┌─────────────────────┐   │
                    │  │ inventory.reserved  │   │
                    │  │ ┌─────────────────┐ │   │
                    │  │ │ (empty now)     │ │   │
                    │  │ └─────────────────┘ │   │
                    │  └─────────────────────┘   │
                    └──────────────┬──────────────┘
                                   │
                  ┌────────────────┬────────────────┐
                  │                │                │
                  ▼                ▼                ▼
        ┌──────────────────┐  ┌──────────────────┐
        │ Payment Service  │  │Inventory Service │
        │  (Subscriber 1)  │  │ (Subscriber 2)   │
        │                  │  │                  │
        │ Listening to:    │  │ Listening to:    │
        │ • order.created  │  │ • order.created  │
        │ • payment.failed │  │ • payment.failed │
        │ • inventory.ok   │  │ • inventory.ok   │
        │                  │  │                  │
        │ When message     │  │ When message     │
        │ arrives:         │  │ arrives:         │
        │                  │  │                  │
        │ 1. Process       │  │ 1. Validate      │
        │ 2. Call Stripe   │  │ 2. Check stock   │
        │ 3. Publish       │  │ 3. Publish       │
        │    result        │  │    result        │
        └──────────────────┘  └──────────────────┘


OFFSET & CONSUMER GROUPS
────────────────────────

Kafka tracks which messages each service has read:

Topic: order.created

Partition 0:
┌──────┬──────┬──────┬──────┬──────┐
│ MSG1 │ MSG2 │ MSG3 │ MSG4 │ MSG5 │
└──────┴──────┴──────┴──────┴──────┘
  0      1      2      3      4

Payment Service (Consumer Group: payment-group)
├─ Current Offset: 3
├─ Last message read: MSG3
└─ Next: Will read MSG4

Inventory Service (Consumer Group: inventory-group)
├─ Current Offset: 4
├─ Last message read: MSG4
└─ Next: Will read MSG5

Result:
└─ Each service independently consumes messages
└─ One service being slow doesn't affect others
└─ No lost messages (Kafka persists everything)
```

---

## 🔌 Port Mapping & Networking

```
EXTERNAL (Host) ←→ INTERNAL (Docker Network)
═════════════════════════════════════════════

Your Computer (Host)                Docker Network (Container Network)
─────────────────────────────────────────────────────────────────────

Browser on PC                       API Gateway Container
  │                                  │
  └─ localhost:8080                  └─ 0.0.0.0:8080 (container port)
       │                                 │
       └─ Routed to: 127.0.0.1:8080 ────┘
            (Port forward via docker)


Service to Service (Internal only)
──────────────────────────────────

Payment Service Container          Order Service Container
  │                                  │
  ├─ Address: payment-service        ├─ Address: order-service
  │  (Docker DNS resolves this)      │  (Docker DNS resolves this)
  │                                  │
  └─ http://order-service:8083 ─────→ Connects via Docker bridge network
                                      (port 8083 is internal only)


External Access vs Internal Access
────────────────────────────────────

Customer Service:
┌──────────────────────────────────────────┐
│ External Mapping:                        │
│ Host Port 8081 ←→ Container Port 8081   │
│                                          │
│ Docker Compose:                          │
│ ports:                                   │
│   - "8081:8081"                         │
│        ↑     ↑                          │
│        |     └─ Container internal port │
│        └─ Host machine port             │
│                                          │
│ Access:                                  │
│ FROM OUTSIDE Docker:                     │
│   http://localhost:8081                 │
│                                          │
│ FROM INSIDE Docker (Order Service):      │
│   http://customer-service:8081          │
│   (Service name via Docker DNS)         │
└──────────────────────────────────────────┘


All Ports Your Project Uses
────────────────────────────

Application Services:
├─ API Gateway      :8080  (External access)
├─ Customer Service :8081  (External access)
├─ Inventory Service:8082  (External access)
├─ Order Service    :8083  (External access)
├─ Payment Service  :8084  (External access)
└─ Product Service  :8085  (External access)

Infrastructure:
├─ Config Server    :8888
├─ Eureka Discovery :8761
└─ Admin Dashboard  :9090

Message Queue:
├─ Kafka (External) :9092
└─ Kafka (Internal) :29092

Monitoring:
├─ Prometheus       :9090
├─ Grafana          :3000
└─ Kibana (Logs)    :5601

Databases:
├─ PostgreSQL       :5432
└─ MongoDB          :27017


When Container Communicates:
────────────────────────────

Order Service → Payment Service

❌ WRONG:
  URL = http://localhost:8084/...
  (localhost inside container = the container itself!)

✅ CORRECT:
  URL = http://payment-service:8084/...
  (service name resolved by Docker DNS)
```

---

## 🔧 Troubleshooting Decision Tree

```
SERVICE NOT RESPONDING?
═══════════════════════

START HERE: Is the service running?
│
├─ YES → Go to: "Service Running but Not Responding"
├─ NO → Go to: "Service Crashed"
└─ DON'T KNOW → docker-compose ps


SERVICE CRASHED
────────────────

docker-compose logs order-service | head -50

┌─────────────────────────────────────────────────┐
│ Look for error messages, stack traces           │
└─────────────────────────────────────────────────┘

Error Type: "Cannot find Discovery Server"
├─ Solution:
│  1. docker-compose ps | grep discovery-server
│  2. If not running: docker-compose up -d discovery-server
│  3. Wait 10 seconds
│  4. docker-compose up -d [service-name]

Error Type: "Connection refused on kafka:29092"
├─ Solution:
│  1. docker-compose logs kafka | tail -20
│  2. If says "Starting": wait 15 seconds
│  3. If says "Error": docker-compose restart kafka

Error Type: "Failed to connect to database"
├─ Solution:
│  1. docker-compose ps | grep postgres
│  2. If not running: docker-compose up -d postgres
│  3. Check if DB initialized:
│     docker-compose exec postgres \
│       psql -U postgres -d order_db -c "SELECT 1"

Error Type: "OutOfMemoryError"
├─ Solution:
│  1. docker stats  (Check memory usage)
│  2. docker-compose down
│  3. docker system prune  (Clean up)
│  4. docker-compose up -d


SERVICE RUNNING BUT NOT RESPONDING
──────────────────────────────────

Is it registered in Eureka?
├─ YES → Go to: "Check Health"
└─ NO → Go to: "Eureka Registration Failed"

Check Health
├─ curl http://localhost:8083/actuator/health
│  {
│    "status": "UP",
│    "components": {
│      "db": {"status": "UP"},
│      "kafka": {"status": "UP"},
│      "eureka": {"status": "UP"}
│    }
│  }
├─ All UP? → "Check Logs for Errors"
└─ Something DOWN? → "Fix that component first"

Eureka Registration Failed
├─ Check Eureka server:
│  curl http://localhost:8761/eureka/apps
├─ Is order-service listed?
│  curl http://localhost:8761/eureka/apps/ORDER-SERVICE
├─ If not listed:
│  docker-compose logs order-service | grep -i eureka
│  Look for "Unable to connect" or "Registration failed"
└─ If it says "Retrying": wait 30 seconds, check again

Check Logs for Errors
├─ Full logs:
│  docker-compose logs order-service
├─ Last 50 lines:
│  docker-compose logs order-service | head -50
├─ Real-time logs:
│  docker-compose logs -f order-service
├─ Filter for errors:
│  docker-compose logs order-service | grep -i error
└─ Found error? → Research the error message


SERVICE RETURNS ERROR RESPONSES
───────────────────────────────

curl -v http://localhost:8083/api/orders

400 Bad Request
└─ Invalid input
   ├─ Check request format
   └─ Check required fields

401 Unauthorized
└─ JWT token missing or invalid
   ├─ curl -H "Authorization: Bearer TOKEN" http://...
   └─ Check token in Authorization header

404 Not Found
└─ Endpoint doesn't exist
   ├─ Check API path spelling
   └─ Check API Gateway routing rules

500 Internal Server Error
└─ Service error
   ├─ docker-compose logs order-service | tail -30
   ├─ Look for exception stack trace
   └─ Fix the underlying issue


DATABASE CONNECTION ISSUES
──────────────────────────

Service says: "Cannot connect to database"

Check if database is running:
├─ docker-compose ps | grep postgres

Check if can reach database:
├─ docker-compose exec order-service \
│    psql -h postgres -U postgres -d order_db -c "SELECT 1"

Check database configuration:
├─ Inside order-service container:
│  docker-compose exec order-service bash
│  cat /config/application.yml | grep -A 5 datasource

Common issues:
├─ Wrong hostname: localhost vs postgres
├─ Wrong port: 5432 vs other port
├─ Wrong database name: order_db vs orders_db
└─ Wrong password: check .env file


KAFKA CONNECTIVITY ISSUES
────────────────────────

Service says: "Kafka connection refused"

Check Kafka status:
├─ docker-compose ps | grep kafka

Check Kafka logs:
├─ docker-compose logs kafka | tail -30

Check if Kafka is ready:
├─ docker-compose exec kafka \
│    kafka-broker-api-versions --bootstrap-servers localhost:9092

Test topic connectivity:
├─ docker-compose exec kafka \
│    kafka-console-consumer --bootstrap-servers kafka:29092 --list-topics

Common issues:
├─ Kafka not fully started (takes 10-15 seconds)
│  └─ Wait and retry
├─ Using wrong bootstrap servers:
│   - External: kafka:9092 (from host)
│   - Internal: kafka:29092 (from other containers)
├─ Network issues: docker network inspect
└─ Kafka crashed: docker-compose restart kafka


LOW PERFORMANCE / SLOW RESPONSES
────────────────────────────────

Check CPU/Memory usage:
├─ docker stats
├─ If > 80% CPU: Service overloaded
├─ If > 90% Memory: Out of memory soon

Check service response time:
├─ time curl http://localhost:8083/api/orders/1
├─ > 1 second: Investigate why
│  ├─ Is database slow?
│  ├─ Is external API call slow?
│  └─ Are there cascading service calls?

Check database:
├─ docker-compose exec postgres \
│    psql -U postgres -d order_db -c "SELECT count(*) FROM orders"
├─ If millions of rows: Need index optimization

Check Kafka lag:
├─ Are messages piling up?
│  docker-compose exec kafka \
│    kafka-consumer-groups --bootstrap-servers kafka:29092 \
│    --group order-service-group --describe

Solutions:
├─ Add more service instances: docker-compose up -d --scale order-service=3
├─ Increase memory: Edit docker-compose.yml
├─ Optimize database queries: Add indexes
└─ Check for memory leaks: docker stats over time
```

---

## 📈 Request Flow Through Complete System

```
COMPLETE REQUEST LIFECYCLE
═══════════════════════════

PHASE 1: CLIENT REQUEST
────────────────────────

Browser/App
    │
    ├─ Create request:
    │  POST http://localhost:8080/api/orders
    │  Authorization: Bearer jwt_token_here
    │  Content-Type: application/json
    │  {
    │    "customerId": 5,
    │    "items": [...]
    │  }
    │
    └─ Send request
       ↓


PHASE 2: API GATEWAY PROCESSING
─────────────────────────────────

API Gateway (8080)
    │
    ├─ Receive HTTP request on port 8080
    │
    ├─ Parse JWT token from "Authorization" header
    │  │
    │  └─ JWT valid? ✅ → Continue
    │     JWT invalid? ❌ → Return 401 Unauthorized
    │
    ├─ Check rate limit
    │  │
    │  └─ Under 100 req/min? ✅ → Continue
    │     Over limit? ❌ → Return 429 Too Many Requests
    │
    ├─ Match route:
    │  Pattern: POST /api/orders
    │  Matches rule: → order-service
    │
    ├─ Service lookup via Eureka:
    │  "What's the URL for ORDER-SERVICE?"
    │  Eureka: "http://order-service:8083"
    │
    └─ Forward request
       Content-Type: application/json
       Body: {...}
       ↓


PHASE 3: MICROSERVICE PROCESSING
──────────────────────────────────

Order Service (8083) receives request
    │
    ├─ @PostMapping("/api/orders")
    │  public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest req)
    │
    ├─ Validation:
    │  ├─ customerId not null? ✅
    │  ├─ items not empty? ✅
    │  └─ All items valid? ✅
    │
    ├─ Business Logic:
    │  ├─ Create Order object
    │  ├─ order.setCustomerId(5)
    │  ├─ order.setStatus(OrderStatus.CREATED)
    │  ├─ order.setTotalAmount(calculateTotal(items))
    │
    ├─ Save to Database:
    │  INSERT INTO orders (customer_id, status, total_amount, created_at)
    │  VALUES (5, 'CREATED', 100.00, NOW())
    │  → order_db (PostgreSQL)
    │  → Execution time: ~10ms
    │
    ├─ Publish Event:
    │  OrderEvent event = new OrderEvent()
    │  event.setOrderId(123)
    │  event.setCustomerId(5)
    │  kafkaTemplate.send("order.created", event)
    │  → Kafka receives & queues
    │  → Execution time: ~5ms
    │
    ├─ Create Response:
    │  {
    │    "orderId": 123,
    │    "status": "CREATED",
    │    "totalAmount": 100.00,
    │    "createdAt": "2024-01-15T10:30:00Z"
    │  }
    │
    └─ Send response back (total: ~40ms)
       ↓


PHASE 4: ASYNC PROCESSING (Background)
──────────────────────────────────────

[Order API already returned response!]

Meanwhile in background (parallel):

Kafka Topic: order.created
    │
    ├─→ Payment Service listens
    │   (Subscriber 1)
    │   │
    │   ├─ Receive event
    │   ├─ Charge credit card ($100)
    │   ├─ Stripe API call
    │   ├─ Save payment record
    │   ├─ Publish: payment.processed
    │   └─ Time: ~2 seconds
    │
    └─→ Inventory Service listens
        (Subscriber 2)
        │
        ├─ Receive event
        ├─ Check stock: Product#10 = 50 units
        ├─ Reserve: 2 units
        ├─ Update database
        ├─ Publish: inventory.reserved
        └─ Time: ~100ms


PHASE 5: MONITORING & OBSERVATION
──────────────────────────────────

Prometheus (9090) collects metrics:
    ├─ HTTP request count
    ├─ Request latency
    ├─ Error rate
    ├─ Database query time
    └─ Kafka message lag

Grafana (3000) visualizes:
    ├─ Service health dashboard
    ├─ Request latency graph
    ├─ Error rate alerts
    └─ System performance

Elasticsearch + Logstash + Kibana (ELK Stack):
    ├─ Collects all logs from all services
    ├─ Centralizes in Elasticsearch
    ├─ Searches via Kibana interface
    ├─ Can trace: "Show all Order#123 logs"
    └─ Shows entire journey through system


COMPLETE TIMELINE
─────────────────

T0:00    Client sends request to localhost:8080
T0:05    API Gateway receives, validates JWT
T0:10    API Gateway rate limits check
T0:15    API Gateway routes to order-service
T0:20    Order Service receives request
T0:30    Order created in database (10ms)
T0:35    Event published to Kafka (5ms)
T0:40    ✅ Response returned to client!

[Client got response in ~40ms!]

T0:50    Payment Service gets event from Kafka
T1:00    Inventory Service gets event from Kafka
T1:05    Payment Service calls Stripe API
T1:10    Inventory Service reserves stock
T2:10    Stripe responds (2 sec processing)
T2:15    Payment Service publishes payment.processed
T2:20    Inventory Service publishes inventory.reserved
T2:25    Order Service receives both events
T2:30    Order updated to PAID + STOCK_RESERVED

[Async background work done!]

Total wait for payment: ~2.5 seconds
(If synchronous: would be blocking the whole time)
```

---

## ✅ Architecture Checklist

Looking at your system, verify these are in place:

```
Service Independence:
├─ ✅ Each service has own database (Customer→PG, Order→PG, Inventory→Mongo)
├─ ✅ No direct DB queries between services
├─ ✅ Communication via REST APIs and Events
└─ ✅ Services can start/stop independently

API Gateway:
├─ ✅ Single entry point for clients (localhost:8080)
├─ ✅ JWT authentication
├─ ✅ Rate limiting
├─ ✅ Request routing to services
└─ ✅ Centralized security

Service Discovery:
├─ ✅ Eureka server running (8761)
├─ ✅ Services register on startup
├─ ✅ Services deregister on shutdown
├─ ✅ Health checks every 30 seconds
└─ ✅ Services use service names, not IPs

Event-Driven Communication:
├─ ✅ Kafka running (9092/29092)
├─ ✅ Topics defined (order.created, payment.*, inventory.*, etc.)
├─ ✅ Services publish events
├─ ✅ Services subscribe to events
└─ ✅ SAGA pattern for distributed transactions

Resilience Patterns:
├─ ✅ Retry logic with backoff
├─ ✅ Circuit breaker for external calls
├─ ✅ Timeout handling
├─ ✅ Fallback responses
└─ ✅ Error handling & logging

Configuration Management:
├─ ✅ Config Server running (8888)
├─ ✅ Configuration in Git repository
├─ ✅ Services fetch config on startup
└─ ✅ Config refresh capability

Monitoring & Observability:
├─ ✅ Prometheus metrics (9090)
├─ ✅ Grafana dashboards (3000)
├─ ✅ ELK Stack for logging (Kibana 5601)
├─ ✅ Centralized logging
└─ ✅ Health check endpoints
```

---

## 🎯 Key Insights

1. **Asynchronous Processing** - Order API returns in 40ms while payments process in background
2. **Parallel Execution** - Payment & Inventory services work simultaneously
3. **Resilience** - One service failure doesn't crash others
4. **Observability** - Trace requests through entire system
5. **Independent Scaling** - Scale only services that need it

---

**Next:** Move to MICROSERVICES_ZERO_TO_HERO.md for deep-dive into patterns and production practices!
