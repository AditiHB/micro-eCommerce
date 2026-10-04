# Compliance & Monitoring Guide

## Overview

This document outlines the compliance monitoring infrastructure for the micro-ecommerce platform, covering certificate expiration monitoring, TLS security metrics, secrets access auditing, and compliance validation for PCI-DSS and GDPR.

## Architecture Components

### Monitoring Stack
- **Prometheus**: Metrics collection and alerting
- **Grafana**: Dashboards and visualization
- **AlertManager**: Alert routing and notifications
- **Elasticsearch**: Log storage and analysis
- **Kibana**: Log visualization and search
- **Filebeat**: Log shipping

### Security Components
- **Vault**: Secrets management
- **Cert-Manager**: Certificate lifecycle management
- **Network Policies**: Kubernetes network segmentation
- **RBAC**: Role-based access control
- **Audit Logging**: Comprehensive access logging

## Certificate Expiration Monitoring

### Setup

```bash
# Deploy certificate exporter
kubectl apply -f infrastructure/monitoring/certificate-exporter.yaml

# Deploy alert rules
kubectl apply -f infrastructure/monitoring/certificate-alerts.yaml
```

### Alert Thresholds

- **30 days before expiration**: Warning alert (yellow)
- **7 days before expiration**: Critical alert (red)
- **1 day before expiration**: Emergency alert (red, paging)
- **After expiration**: Service disabled immediately

### Automatic Renewal

Certificates are automatically renewed via cert-manager if configured:

```bash
# Check cert-manager status
kubectl get certificates -n ecommerce

# View certificate details
kubectl describe certificate payment-service-tls -n ecommerce

# Manually trigger renewal if needed
kubectl annotate certificate payment-service-tls \
  cert-manager.io/issue-temporary-certificate="true" --overwrite
```

## TLS Security Metrics

### Monitored Metrics

- TLS version in use (1.2, 1.3)
- Cipher suite strength (strong, medium, weak)
- Certificate chain validation status
- TLS handshake duration
- Certificate expiration countdown

### Grafana Dashboards

Access Grafana at `http://localhost:3000` (default admin/admin)

**Dashboards:**
1. TLS/SSL Security Overview
2. Certificate Expiration Tracking
3. Cipher Suite Analysis
4. Handshake Performance

### Viewing Metrics

```bash
# Query Prometheus
curl 'http://localhost:9090/api/v1/query?query=ssl_certificate_not_after'

# View metrics in Grafana
# Navigate to Dashboards > TLS/SSL Security Overview
```

## Secrets Access Audit Logging

### Vault Audit Configuration

```bash
# Check Vault audit log
kubectl logs vault-0 -c vault -f | grep audit

# View Elasticsearch audit logs
curl 'http://localhost:9200/vault-audit-*/_search?q=operation:create'
```

### Audit Events Captured

- Secret creation
- Secret updates
- Secret deletions
- Secret access (get, list)
- Authentication attempts
- Authorization denials
- Policy changes

### Kibana Dashboards

Access Kibana at `http://localhost:5601`

**Dashboards:**
1. Secrets Access Audit Dashboard
   - Who accessed what secret
   - When secrets were accessed
   - Successful vs failed attempts
   - Secrets accessed outside business hours

### Sample Audit Queries

```json
// Failed access attempts
{
  "query": {
    "bool": {
      "must": [
        {"match": {"type": "response"}},
        {"match": {"error": "*"}}
      ]
    }
  }
}

// Secrets accessed outside business hours (8AM-6PM)
{
  "query": {
    "bool": {
      "must": [
        {"match": {"type": "request"}},
        {"match": {"path": "secret/*"}}
      ],
      "filter": {
        "script": {
          "script": "doc['timestamp'].value.getHourOfDay() < 8 || doc['timestamp'].value.getHourOfDay() > 18"
        }
      }
    }
  }
}
```

## PCI-DSS Compliance Validation

### Running PCI-DSS Checks

```bash
# Run compliance checks
bash infrastructure/compliance/pci-dss-check.sh

# View report
cat /tmp/pci-dss-report-*.txt
```

### PCI-DSS Requirements Mapped

| Requirement | Control | Status |
|-------------|---------|--------|
| Req 2 | Hardened Configuration | ✓ Automated |
| Req 4 | TLS 1.2+ Encryption | ✓ Enforced |
| Req 6 | Secure Development | ✓ Validated |
| Req 8 | User Authentication | ✓ Monitored |
| Req 10 | Logging & Monitoring | ✓ Centralized |

### Compliance Score

Compliance percentage is calculated from passed checks:
```
Compliance Score = (Passed Checks / Total Checks) × 100%
```

Target: **100% compliance** on all checks

## GDPR Compliance Validation

### Running GDPR Checks

```bash
# Run compliance checks
bash infrastructure/compliance/gdpr-check.sh

# View report
cat /tmp/gdpr-report-*.txt
```

### GDPR Requirements Mapped

| Article | Requirement | Status |
|---------|-------------|--------|
| Art 5 | Data Integrity & Confidentiality | ✓ Validated |
| Art 25 | Privacy by Design | ✓ Implemented |
| Art 32 | Security Measures | ✓ Deployed |
| Art 33 | Breach Notification | ✓ Configured |
| Art 34 | Individual Notification | ✓ Ready |

### Data Subject Rights

Implemented endpoints:
- `/api/gdpr/export` - Data export (Right to Access)
- `/api/gdpr/rectify` - Data correction (Right to Rectification)
- `/api/gdpr/erase` - Data deletion (Right to Erasure)
- `/api/gdpr/restrict` - Processing restriction
- `/api/gdpr/portability` - Data portability

## SSL Labs & Security Audits

### Running SSL Labs Tests

```bash
# Test single host
bash infrastructure/scripts/ssl-labs-test.sh

# View report
cat /tmp/ssl-audit-report-*.txt
```

### Test Coverage

- TLS protocol support (1.2, 1.3, legacy versions)
- Cipher suite strength
- Certificate validation
- Handshake performance
- Security headers

### External Audit Integration

```bash
# Run comprehensive security audit
bash infrastructure/scripts/security-audit.sh

# Generates report with findings
cat /tmp/security-audit-*.txt
```

## Monitoring & Alerting Stack

### Starting the Stack

Locally, Prometheus/Grafana/Alertmanager/Elasticsearch/Kibana/Loki are started via the main `docker-compose.yml`'s `observability` profile (see [SETUP_AND_DEPLOYMENT.md](SETUP_AND_DEPLOYMENT.md#monitoring-stack)):

```bash
docker compose --profile observability up -d

# Verify services
docker compose ps
```

Filebeat, the standalone certificate-exporter container, and Vault shown in the architecture above are part of the **Kubernetes** compliance deployment (see the `kubectl apply` commands in this doc and [KUBERNETES_DEPLOYMENT.md](KUBERNETES_DEPLOYMENT.md)/[SECRETS_MANAGEMENT.md](SECRETS_MANAGEMENT.md)) - they aren't part of the local Compose stack.

### Service Health Checks

```bash
# Prometheus
curl http://localhost:9090/-/healthy

# Grafana
curl http://localhost:3000/api/health

# AlertManager
curl http://localhost:9093/-/healthy

# Elasticsearch
curl http://localhost:9200/_cat/health

# Kibana
curl http://localhost:5601/api/status
```

## Audit Logging for Compliance

### Kubernetes Audit Logging

Configured in `infrastructure/logging/audit-logging.yaml`

```bash
# View audit logs
kubectl logs -f deployment/audit-logger -n kube-system

# Search for specific events
journalctl -u apiserver | grep audit | grep secret
```

### Long-Term Retention

Audit logs are retained for 7 years (compliance requirement):
1. Active logs: 30 days on disk
2. Archive storage: Elasticsearch
3. Long-term storage: Compressed archives

### Retention Policy

```bash
# Check retention status
curl -X GET "http://localhost:9200/_ilm/policy/audit-retention-policy"

# Manually trigger archival
kubectl exec -it vault-0 -n vault -- bash
vault audit list
```

## Incident Response Procedures

### Automated Incident Response

The incident response system automatically handles:

1. **Certificate Expiration**
   ```bash
   bash infrastructure/scripts/incident-response.sh cert-expiry /path/to/cert 30
   ```

2. **Secrets Compromise**
   ```bash
   bash infrastructure/scripts/incident-response.sh secrets-compromise api-key "unauthorized-access-detected"
   ```

3. **TLS Handshake Failure**
   ```bash
   bash infrastructure/scripts/incident-response.sh tls-failure payment-service:8443 "certificate-invalid"
   ```

4. **Unauthorized Access**
   ```bash
   bash infrastructure/scripts/incident-response.sh unauthorized-access /api/payment user123 5
   ```

5. **Data Breach**
   ```bash
   bash infrastructure/scripts/incident-response.sh data-breach "payment-data-leak" 10000
   ```

### Manual Incident Response

See `INCIDENT_RESPONSE.md` for detailed procedures.

## Regular Compliance Reviews

### Weekly Reviews
- Certificate expiration status
- TLS handshake performance
- Failed authentication attempts
- Unauthorized access patterns

### Monthly Reviews
- Secrets access logs
- Compliance score trends
- Security metrics analysis
- Incident summary report

### Quarterly Reviews
- Full PCI-DSS compliance audit
- Full GDPR compliance audit
- Security audit findings
- Remediation status

### Annual Reviews
- Compliance certification
- External audit preparation
- Policy updates
- Training requirements

## Troubleshooting

### Certificate Not Renewing

```bash
# Check cert-manager status
kubectl get crd certificates.cert-manager.io

# Check certificate resource
kubectl describe certificate payment-service-tls -n ecommerce

# Check cert-manager logs
kubectl logs -f deployment/cert-manager -n cert-manager
```

### Missing Metrics

```bash
# Verify Prometheus scrape targets
curl http://localhost:9090/api/v1/targets

# Check exporter connectivity
curl http://certificate-exporter:9090/metrics

# Verify service discovery
kubectl get endpoints -n monitoring
```

### Elasticsearch Connection Issues

```bash
# Check Elasticsearch status
curl http://localhost:9200/_cluster/health

# Verify Filebeat connectivity
docker logs filebeat

# Check log pipeline
curl http://localhost:9200/_cat/pipelines
```

## Support & Contact

- **Security Team**: security-team@example.com
- **Compliance Officer**: compliance@example.com
- **On-Call Engineer**: See PagerDuty
- **Incident Hotline**: +1-555-SECURITY
