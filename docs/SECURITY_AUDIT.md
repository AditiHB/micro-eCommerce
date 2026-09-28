# Security Audit Procedures

## Overview

Security audits are essential for validating the effectiveness of security controls and ensuring compliance with industry standards (PCI-DSS, GDPR, SOC2).

## Audit Types

### 1. Automated Compliance Audits

Run automated checks for PCI-DSS, GDPR, and general security.

#### PCI-DSS Audit

```bash
bash infrastructure/compliance/pci-dss-check.sh
# Output: /tmp/pci-dss-report-YYYYMMDD_HHMMSS.txt
```

**Validates:**
- TLS 1.2+ enforcement
- Weak cipher detection
- Certificate chain validation
- Secrets exposure
- Access logging
- Compliance scoring

#### GDPR Audit

```bash
bash infrastructure/compliance/gdpr-check.sh
# Output: /tmp/gdpr-report-YYYYMMDD_HHMMSS.txt
```

**Validates:**
- Data encryption in transit/at rest
- Consent mechanisms
- Data retention policies
- Audit logging
- Data subject rights
- Breach notification procedures

### 2. SSL/TLS Security Audit

Comprehensive TLS configuration and certificate audit.

```bash
bash infrastructure/scripts/ssl-labs-test.sh
# Output: /tmp/ssl-audit-report-YYYYMMDD_HHMMSS.txt
```

**Tests:**
- TLS protocol versions (1.2, 1.3, legacy)
- Cipher suite strength
- Certificate validity and chain
- Certificate expiration
- Handshake performance
- Security headers

### 3. Comprehensive Security Audit

Full vulnerability and security control scan.

```bash
bash infrastructure/scripts/security-audit.sh
# Output: /tmp/security-audit-YYYYMMDD_HHMMSS.txt
```

**Covers:**
- OpenSSL certificate validation
- Cipher strength analysis
- Secrets exposure detection
- Dependency vulnerabilities
- Input validation controls
- Authentication & authorization
- Logging & monitoring
- Container security

## Audit Schedule

### Daily Automated Checks
- [ ] Certificate expiration check (Prometheus)
- [ ] TLS handshake success rate
- [ ] Failed authentication attempts
- [ ] Unauthorized access patterns

**Execute:**
```bash
# Run daily via cron
0 1 * * * /usr/local/bin/daily-security-checks.sh
```

### Weekly Audits
- [ ] SSL/TLS configuration
- [ ] Secrets access audit
- [ ] RBAC enforcement verification
- [ ] Network policy validation

**Schedule:**
```bash
# Every Monday at 2 AM
0 2 * * 1 bash infrastructure/scripts/ssl-labs-test.sh
```

### Monthly Audits
- [ ] Comprehensive security audit
- [ ] PCI-DSS compliance check
- [ ] GDPR compliance check
- [ ] Audit log review

**Schedule:**
```bash
# First day of month at 3 AM
0 3 1 * * bash infrastructure/compliance/pci-dss-check.sh
```

### Quarterly Audits
- [ ] External vulnerability scan
- [ ] Penetration testing (Q2, Q4)
- [ ] Compliance certification audit
- [ ] Policy effectiveness review

### Annual Audits
- [ ] Full PCI-DSS assessment
- [ ] GDPR compliance certification
- [ ] SOC2 Type II audit
- [ ] External security assessment

## Audit Report Analysis

### Report Location
```
/tmp/pci-dss-report-YYYYMMDD_HHMMSS.txt
/tmp/gdpr-report-YYYYMMDD_HHMMSS.txt
/tmp/ssl-audit-report-YYYYMMDD_HHMMSS.txt
/tmp/security-audit-YYYYMMDD_HHMMSS.txt
```

### Report Format

Each report contains:
1. **Executive Summary**
   - Total checks performed
   - Passed/failed counts
   - Compliance percentage
   - Critical findings

2. **Detailed Findings**
   - Category
   - Check description
   - Result (PASS/FAIL)
   - Details/evidence
   - Remediation steps

3. **Compliance Score**
   - Overall percentage
   - By-category breakdown
   - Trend analysis

### Interpreting Results

**Compliance Score:**
- 100%: Full compliance
- 95-99%: Minor findings, immediate action
- 80-94%: Multiple findings, urgent remediation
- <80%: Critical issues, escalate immediately

**Finding Severity:**
- **Critical**: Immediate security risk, requires immediate action
- **High**: Compliance requirement, fix within 7 days
- **Medium**: Best practice, fix within 30 days
- **Low**: Enhancement, fix within 90 days

## Remediation Process

### Step 1: Root Cause Analysis

For each failed check:

```bash
# Gather evidence
ls -la /etc/ssl/certs/
openssl x509 -in certificate.pem -noout -text
curl -I https://service:8443

# Log details
echo "Root cause: [your analysis]" >> remediation-log.txt
```

### Step 2: Implement Fix

Apply remediation based on finding type:

#### TLS/Certificate Issues

```bash
# Renew certificate
kubectl certificate approve [csr-name] -n ecommerce

# Verify renewal
kubectl get certificate -n ecommerce

# Restart services to pick up new cert
kubectl rollout restart deployment/api-gateway -n ecommerce
```

#### Secrets Exposure

```bash
# Rotate exposed secret
bash infrastructure/scripts/incident-response.sh secrets-compromise [secret-name] "exposed-in-audit"

# Verify no secrets in code
grep -r "api_key\|password\|secret" src/ --include="*.java" --include="*.py" --include="*.js"
```

#### Missing Controls

```bash
# Deploy missing controls
kubectl apply -f infrastructure/monitoring/certificate-alerts.yaml
kubectl apply -f k8s/21-network-policies.yaml
kubectl apply -f k8s/22-rbac-policies.yaml
```

### Step 3: Verification

Re-run the same audit to verify remediation:

```bash
# Re-run failing audit
bash infrastructure/compliance/pci-dss-check.sh

# Verify specific check
openssl s_client -connect localhost:8443 -tls1_2 </dev/null | grep "Protocol"
```

### Step 4: Documentation

Document remediation for audit trail:

```bash
cat > remediation-record.txt << EOF
Finding: [Finding ID]
Severity: [Critical/High/Medium/Low]
Root Cause: [Cause]
Remediation: [Actions taken]
Verification: [How verified]
Date Completed: $(date)
Approved By: [Name]
EOF
```

## Audit Log Access

### Kubernetes Audit Logs

```bash
# View audit logs from kube-apiserver
journalctl -u kubelet | grep audit

# Search for specific events
journalctl | grep "secret"

# Check audit policy
kubectl describe configmap audit-policy -n kube-system
```

### Application Audit Logs

```bash
# Check Elasticsearch audit indices
curl 'http://localhost:9200/_cat/indices?v' | grep audit

# Search audit logs
curl -X POST "http://localhost:9200/vault-audit-*/_search" \
  -H 'Content-Type: application/json' \
  -d '{"query": {"match_all": {}}}'

# View in Kibana
# Browse to http://localhost:5601
# Discover tab -> vault-audit-* index
```

### Vault Audit Logs

```bash
# View Vault audit output
kubectl logs vault-0 -c vault | grep audit

# List audit devices
vault audit list

# Read audit logs from file
cat /vault/logs/audit.log | head -50
```

## Compliance Reporting

### Executive Report

```bash
# Generate executive summary
cat > /tmp/compliance-summary-$(date +%Y%m%d).txt << EOF
COMPLIANCE SUMMARY REPORT
Generated: $(date)

PCI-DSS Status:
$(tail -10 /tmp/pci-dss-report-*.txt | grep -E "Score|Passed|Failed")

GDPR Status:
$(tail -10 /tmp/gdpr-report-*.txt | grep -E "Score|Passed|Failed")

TLS Security:
$(tail -10 /tmp/ssl-audit-report-*.txt | grep -E "Score|Passed|Failed")

Overall Compliance: [PASS/FAIL]
Critical Findings: [NUMBER]
Required Actions: [LIST]
Next Audit: $(date -d '+1 month' +%Y-%m-%d)
EOF
```

### Compliance Dashboard

Access Grafana dashboard:
```
http://localhost:3000
Dashboard: Compliance Metrics
Metrics:
- PCI-DSS Compliance Score
- GDPR Compliance Score
- Certificate Expiration Status
- TLS Security Score
- Secrets Access Audit Summary
```

### Stakeholder Communication

**Monthly Report Distribution:**
1. C-Level (CEO, CFO, CISO)
   - Executive summary only
   - Critical findings
   - Risk assessment

2. Compliance Team
   - Full audit reports
   - Detailed findings
   - Remediation status

3. Engineering Teams
   - Technical findings
   - Remediation steps
   - Verification process

4. Board/Audit Committee
   - Quarterly comprehensive report
   - Compliance certification
   - Year-over-year trends

## Audit Automation

### Scheduled Audits

```bash
#!/bin/bash
# setup-audit-schedule.sh
# Configures automated audit schedule

# Daily certificate checks
cat > /etc/cron.d/daily-cert-check << 'EOF'
0 1 * * * root bash infrastructure/scripts/ssl-labs-test.sh >> /var/log/audits/daily-cert.log 2>&1
EOF

# Weekly compliance audits
cat > /etc/cron.d/weekly-compliance << 'EOF'
0 2 * * 1 root bash infrastructure/compliance/pci-dss-check.sh >> /var/log/audits/pci-dss.log 2>&1
0 3 * * 1 root bash infrastructure/compliance/gdpr-check.sh >> /var/log/audits/gdpr.log 2>&1
EOF

# Monthly full audit
cat > /etc/cron.d/monthly-full-audit << 'EOF'
0 4 1 * * root bash infrastructure/scripts/security-audit.sh >> /var/log/audits/full-security.log 2>&1
EOF
```

### Audit Notifications

```bash
# Configure Slack notifications
export SLACK_WEBHOOK="https://hooks.slack.com/services/YOUR/WEBHOOK/URL"

# Configure PagerDuty
export PAGERDUTY_KEY="YOUR_PAGERDUTY_INTEGRATION_KEY"

# Run with notifications
bash infrastructure/scripts/incident-response.sh cert-expiry [cert] [days]
```

## Audit Trail Protection

### Immutable Audit Logs

```bash
# Enable append-only audit logs
echo "o" >> /var/log/audit/audit.log  # Set append-only flag
chattr +a /var/log/audit/audit.log    # Make immutable

# Verify immutability
lsattr /var/log/audit/audit.log
```

### Log Retention

```bash
# Configure Elasticsearch retention policy
curl -X PUT "http://localhost:9200/_ilm/policy/audit-retention-policy" \
  -H 'Content-Type: application/json' \
  -d @infrastructure/logging/audit-logging.yaml
```

### Log Access Control

```bash
# Restrict audit log permissions
chmod 600 /var/log/audit/audit.log
chown root:root /var/log/audit/audit.log

# Verify RBAC
kubectl get rolebindings -n kube-system | grep audit
```

## Best Practices

1. **Regular Testing**: Run audits on schedule, not just before compliance deadlines
2. **Document Everything**: Maintain records of all audits and remediation
3. **Automate Where Possible**: Use scripts and monitoring to catch issues early
4. **Escalate Appropriately**: Critical findings require immediate attention
5. **Share Results**: Communicate findings to relevant stakeholders
6. **Continuous Improvement**: Use audit findings to improve processes
7. **Stay Updated**: Review industry standards and update audit procedures

## Support

- **Compliance Officer**: compliance@example.com
- **Security Team**: security-team@example.com
- **Audit Coordinator**: audit@example.com

## See Also

- `COMPLIANCE_MONITORING.md` - Detailed monitoring setup
- `PCI_DSS_CHECKLIST.md` - PCI-DSS requirements
- `GDPR_CHECKLIST.md` - GDPR requirements
- `INCIDENT_RESPONSE.md` - Incident response procedures
