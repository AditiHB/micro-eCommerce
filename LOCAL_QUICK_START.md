# Quick Start Guide - Local Infrastructure

Get the entire microservices infrastructure running in 5 minutes!

---

## 🚀 Super Quick Start (5 minutes)

### Prerequisites Check
```bash
# Verify you have Docker and Docker Compose
docker --version          # Should be 24.x+
docker-compose --version  # Should be 2.x+

# For building from source (optional)
java -version            # Should be 21+
mvn --version            # Should be 3.9+
```

### One Command Setup
```bash
# Clone and setup
git clone https://github.com/CleanCoder007/micro-eCommerce.git
cd micro-eCommerce

# Use the automated setup script
./scripts/local-setup.sh full

# Or do it manually:
mvn clean package -DskipTests  # ~5-10 minutes
docker-compose up -d           # Wait 30-60 seconds for startup

# Verify everything is running
docker-compose ps
```

### Access Services
```
API Gateway:       http://localhost:8080
Swagger UI:        http://localhost:8080/swagger-ui.html
Grafana:           http://localhost:3000         (admin/admin123)
Prometheus:        http://localhost:9090
Kibana:            http://localhost:5601
Alertmanager:      http://localhost:9093
```

---

## 📋 Common Commands

### Build
```bash
./scripts/local-setup.sh build      # Build all services
mvn clean package -DskipTests       # Fast build without tests
mvn clean package                   # Build with tests
```

### Start/Stop
```bash
./scripts/local-setup.sh start      # Start all services
./scripts/local-setup.sh stop       # Stop all services
./scripts/local-setup.sh reset      # Stop and remove data
docker-compose up -d                # Start in background
docker-compose down                 # Stop services
docker-compose down -v              # Stop and remove volumes
```

### Monitor
```bash
./scripts/local-setup.sh health     # Check all services
./scripts/local-setup.sh logs       # View all logs
docker-compose ps                   # List containers
docker-compose logs -f order-service # Follow specific service logs
```

### Clean
```bash
./scripts/local-setup.sh clean      # Remove all Docker resources
mvn clean                           # Clean Maven build
docker system prune -a              # Clean unused Docker resources
```

---

## 📊 Quick Service Reference

| Service | Port | Purpose |
|---------|------|---------|
| **API Gateway** | 8080 | Main entry point for all APIs |
| **Customer Service** | 8081 | Customer CRUD operations |
| **Inventory Service** | 8082 | Product inventory management |
| **Order Service** | 8083 | Order creation & tracking |
| **Payment Service** | 8084 | Payment processing |
| **Config Server** | 8888 | Centralized configuration |
| **Eureka Discovery** | 8761 | Service discovery |
| **Kafka** | 9092 | Event streaming broker |
| **Zookeeper** | 2181 | Kafka coordination |
| **Prometheus** | 9090 | Metrics storage |
| **Grafana** | 3000 | Metrics visualization |
| **Elasticsearch** | 9200 | Log storage |
| **Kibana** | 5601 | Log visualization |
| **Alertmanager** | 9093 | Alert management |

---

## 🧪 Quick Test

### Test API Gateway
```bash
# Health check
curl http://localhost:8080/actuator/health

# Create customer
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{"name":"John","email":"john@example.com"}'

# Get customers
curl http://localhost:8080/api/customers

# View in Swagger UI
open http://localhost:8080/swagger-ui.html
```

### View Metrics
```bash
# Prometheus queries
open http://localhost:9090

# Example queries:
# - ecommerce_orders_created
# - ecommerce_payment_processing_time
# - rate(http_requests_total[5m])
```

### View Logs
```bash
# Kibana logs
open http://localhost:5601

# Or view raw Docker logs
docker-compose logs -f order-service
docker-compose logs -f payment-service
```

### View Dashboards
```bash
# Grafana dashboards
open http://localhost:3000
# Login: admin / admin123
# Browse: Dashboards → eCommerce Microservices
```

---

## 🔧 Troubleshooting

### Services won't start?
```bash
# Check logs
docker-compose logs api-gateway

# Ensure enough resources
docker system df    # Check disk space

# Restart services
docker-compose restart
```

### Port already in use?
```bash
# Find process using port
lsof -i :8080

# Kill it
kill -9 <PID>

# Or use different port in docker-compose.yml
```

### Build fails?
```bash
# Check Java version
java -version          # Needs to be 21+

# Clean Maven cache
rm -rf ~/.m2/repository
mvn clean package -DskipTests

# Check specific module
mvn clean package -f services/order-service/pom.xml
```

### Can't connect to containers?
```bash
# Verify containers are running
docker-compose ps

# Check network
docker network ls
docker network inspect micro-ecommerce_default

# Restart Docker
docker-compose restart
```

---

## 📚 Documentation

For detailed information, see:

- **LOCAL_INFRASTRUCTURE_SETUP.md** - Complete infrastructure guide
- **SETUP_AND_DEPLOYMENT.md** - Setup and deployment guide
- **ARCHITECTURE.md** - System architecture
- **SAGA_PATTERN_GUIDE.md** - Saga pattern with compensation

---

## 🎯 Next Steps

After startup, try:

1. **Explore APIs**
   - Open http://localhost:8080/swagger-ui.html
   - Create a customer
   - Create an order
   - View order status

2. **Monitor Metrics**
   - Open http://localhost:3000 (Grafana)
   - Login: admin/admin123
   - View "eCommerce Microservices" dashboard

3. **Check Logs**
   - Open http://localhost:5601 (Kibana)
   - View order, payment, and inventory service logs

4. **Test Saga Pattern**
   - Create order with payment
   - Watch compensation trigger on failure
   - See inventory and payment rollback

---

## 💡 Tips

- **First time slow?** Docker pulls images, builds containers. Wait 5-10 minutes.
- **Need fresh start?** Run `./scripts/local-setup.sh reset` then `./scripts/local-setup.sh start`
- **Want to debug?** Use `docker-compose logs -f <service>` to follow logs in real-time
- **Having issues?** Check LOCAL_INFRASTRUCTURE_SETUP.md troubleshooting section
- **Script help?** Run `./scripts/local-setup.sh help`

---

## ⚡ Advanced

### View Kafka topics
```bash
docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

### Inspect H2 database
```bash
# Customer Service H2 console
open http://localhost:8081/h2-console
# JDBC URL: jdbc:h2:mem:customer_db
# Username: sa
# Password: (leave empty)
```

### Check Elasticsearch
```bash
curl http://localhost:9200/_cluster/health
curl http://localhost:9200/_cat/indices
```

### Manual service stop/start
```bash
docker-compose stop order-service
docker-compose start order-service
```

---

**Time to first working system: ~5-10 minutes ⏱️**

Happy coding! 🚀
