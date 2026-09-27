# Micro-eCommerce Monitoring Stack

This directory contains the complete monitoring, alerting, and observability setup for the micro-eCommerce platform using Prometheus, Grafana, Alertmanager, and the ELK Stack.

## Architecture Overview

The monitoring stack is composed of:

1. **Prometheus** - Time-series database for metrics collection
2. **Grafana** - Visualization and dashboarding platform
3. **Alertmanager** - Alert routing and notifications
4. **Node Exporter** - System-level metrics collection
5. **Elasticsearch Exporter** - Elasticsearch cluster metrics
6. **Loki** - Log aggregation system
7. **Elasticsearch** - Log storage and indexing
8. **Kibana** - Log visualization

## Components

### Prometheus Configuration

**File:** `prometheus.yml`

Prometheus is configured with:
- 15-second scrape interval
- 15-second evaluation interval
- Service discovery for all microservices
- Metrics collection endpoints:
  - API Gateway (8080)
  - Customer Service (8081)
  - Inventory Service (8082)
  - Order Service (8083)
  - Payment Service (8084)
  - Kafka metrics
  - Elasticsearch metrics
  - System metrics (Node Exporter)

### Alert Rules

**File:** `alert-rules.yml`

Comprehensive alert rules organized into groups:

#### Microservices Alerts
- Service availability monitoring
- Service down detection (1-minute threshold)
- CPU usage > 85%
- Memory usage > 85%
- Disk usage > 85%

#### Application Metrics
- HTTP error rate > 5% (critical)
- Request latency (P95 > 1s)
- Slow requests detection (P99 > 2s)
- High queue depth (> 100 requests)

#### Business Metrics
- Order processing SLA violation (P99 > 30s)
- Low order throughput (< 1 order/sec)
- Payment failure rate > 2%
- Low inventory stock (< 10 units)
- Customer acquisition anomalies

#### Infrastructure Alerts
- Kafka consumer lag > 10,000 messages
- Elasticsearch cluster health issues
- Elasticsearch disk usage > 85%
- Zookeeper availability
- Dead letter queue buildup

#### SLA & Availability
- System availability degradation (< 95%)
- API Gateway unavailability
- Database connection pool depletion

### Alertmanager Configuration

**File:** `alertmanager.yml`

Alert routing strategy:
- **Critical Alerts** - Immediate notification via Slack, PagerDuty, and Email
- **SLA Violations** - PagerDuty and Email with 5-second group wait
- **Business Alerts** - Slack and Email with 1-minute group wait
- **Infrastructure Alerts** - Slack with 30-second group wait
- **Performance Warnings** - Email with 5-minute group wait
- **Resource Warnings** - Slack with 5-minute group wait

Alert inhibition rules prevent duplicate notifications:
- Don't send warnings if critical alert exists
- Don't send infrastructure alerts if service-down alert exists

### Notification Templates

**File:** `alertmanager-templates.tmpl`

Customizable templates for:
- Slack notifications
- Email notifications
- PagerDuty events

### Grafana Dashboards

#### 1. System Health Dashboard (`system-health.json`)
- CPU usage trends
- Memory usage patterns
- Request rates
- HTTP response codes distribution

#### 2. Service Metrics Dashboard (`service-metrics.json`)
- Service availability status
- Request latency percentiles (P95, P99)
- Error rates by service
- Request volume distribution

#### 3. Business Metrics Dashboard (`business-metrics.json`)
- Order creation rate
- Revenue rate
- Payment success rate
- Customer acquisition rate

### Grafana Data Sources

**File:** `grafana-datasources.yml`

Configured data sources:
- Prometheus (metrics)
- Elasticsearch (logs)
- Loki (log aggregation)
- Alertmanager (alerts)

## Running the Monitoring Stack

### Prerequisites
- Docker and Docker Compose installed
- Port availability (3000, 9090, 9093, 3100, 9100, 9114)

### Start the Stack

```bash
# From the project root
docker-compose up -d prometheus grafana alertmanager node-exporter elasticsearch-exporter loki

# Or start all services including the application
docker-compose up -d
```

### Accessing Services

- **Grafana Dashboard:** http://localhost:3000 (admin/admin123)
- **Prometheus:** http://localhost:9090
- **Alertmanager:** http://localhost:9093
- **Kibana:** http://localhost:5601
- **Elasticsearch:** http://localhost:9200

## Metrics Collection

### Application Metrics (Spring Boot Actuator)

Microservices expose metrics via Spring Boot Actuator at `/actuator/prometheus`:

```
http_requests_total - Total HTTP requests by status
http_request_duration_seconds - Request latency in seconds
process_cpu_usage - Process-level CPU usage
jvm_memory_used_bytes - JVM memory metrics
db_connection_pool_available - Database connection pool status
orders_created_total - Business metric: Orders created
revenue_total - Business metric: Total revenue
customers_created_total - Business metric: Customer count
payment_processing_failures_total - Payment failures
```

### System Metrics (Node Exporter)

- node_cpu_seconds_total - CPU time
- node_memory_MemTotal_bytes - Total memory
- node_memory_MemAvailable_bytes - Available memory
- node_filesystem_size_bytes - Disk capacity
- node_filesystem_avail_bytes - Available disk space

### Infrastructure Metrics

- elasticsearch_cluster_health_status - Cluster status
- kafka_consumer_lag - Message lag
- zk_up - Zookeeper status

## Configuration Environment Variables

### Alertmanager

Set these environment variables for production use:

```bash
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/WEBHOOK/URL
PAGERDUTY_SERVICE_KEY=your-pagerduty-key
PAGERDUTY_SLA_KEY=your-sla-key
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=your-app-password
```

### Grafana

Default credentials: `admin / admin123`

Change in production by setting environment variables in docker-compose.yml:
```yaml
GF_SECURITY_ADMIN_PASSWORD: your-secure-password
```

## Alert SLA Levels

### Critical (5-minute response)
- Service down
- API Gateway unavailable
- System availability < 95%
- Payment failure rate > 2%

### High (15-minute response)
- High error rate (> 5%)
- High latency (P95 > 1s)
- SLA violation

### Medium (1-hour response)
- High CPU/Memory/Disk usage
- Low order throughput
- Kafka consumer lag

### Low (8-hour response)
- Performance warnings
- Resource utilization alerts

## Maintenance

### Retention Policies

**Prometheus:** 15 days of data
**Elasticsearch:** Configurable via Index Lifecycle Management
**Loki:** Configurable retention

### Backup Strategy

```bash
# Backup Prometheus data
docker cp prometheus:/prometheus ./prometheus-backup

# Backup Grafana dashboards
docker exec grafana grafana-cli admin export-dashboard --

# Backup Alertmanager configuration
cp monitoring/alertmanager.yml ./alertmanager-backup.yml
```

## Scaling Considerations

### Multi-instance Setup

For production environments with multiple service instances:

1. **Service Discovery:** Prometheus supports Eureka SD for automatic instance discovery
2. **Load Balancing:** Route metrics through a load balancer
3. **Remote Storage:** Use remote storage backends (Cortex, Thanos) for long-term storage
4. **High Availability:** Run multiple Prometheus and Alertmanager instances

### Performance Tuning

- Adjust scrape intervals based on required granularity
- Configure appropriate retention periods
- Use metric relabeling to reduce cardinality
- Implement recording rules for complex queries

## Troubleshooting

### Prometheus not scraping metrics

1. Check service connectivity: `curl http://service:port/actuator/prometheus`
2. Verify Prometheus targets: http://localhost:9090/targets
3. Check Prometheus logs: `docker logs prometheus`

### Alerts not firing

1. Verify alert rules syntax: `docker logs prometheus` (look for parse errors)
2. Check Prometheus alert status: http://localhost:9090/alerts
3. Verify Alertmanager: http://localhost:9093

### Grafana dashboards not loading

1. Check data source connectivity: Grafana → Configuration → Data Sources
2. Verify dashboard JSON: `monitoring/grafana-dashboards/`
3. Check Grafana logs: `docker logs grafana`

## Additional Resources

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/grafana/)
- [Alertmanager Documentation](https://prometheus.io/docs/alerting/alertmanager/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

## Best Practices

1. **Alert Fatigue:** Tune alert thresholds to reduce false positives
2. **Notification Routing:** Use alert labels to route to appropriate teams
3. **Dashboard Organization:** Create dashboards for different personas (ops, dev, business)
4. **Metric Naming:** Follow Prometheus naming conventions
5. **SLA Tracking:** Use SLO/SLI metrics for compliance tracking
6. **Regular Reviews:** Periodically review and update alert rules
