# Incident Response Plan

## Overview

This document outlines the incident response procedures for security incidents including certificate expiration, secrets compromise, TLS failures, unauthorized access, and data breaches.

## Incident Classification

### Severity Levels

| Level | Impact | Response Time | Examples |
|-------|--------|---------------|-----------| 
| **Critical** | System down, data breach | Immediate | Certificate expired, active breach, major system failure |
| **High** | Service degraded, security issue | 1 hour | TLS handshake failures, secrets compromise, unauthorized access |
| **Medium** | Minor security concern | 4 hours | Weak cipher detected, expired certificate (30+ days), policy violation |
| **Low** | Information only | 24 hours | Audit findings, best practice recommendations |

## Response Team

### Contacts

- **On-Call Engineer**: [PagerDuty escalation policy]
- **Security Lead**: security-lead@example.com
- **Compliance Officer**: compliance@example.com
- **DPO (Data Protection Officer)**: dpo@example.com
- **Legal Team**: legal@example.com
- **Communications**: comms@example.com
- **Executive Sponsor**: ciso@example.com

### Roles & Responsibilities

**Incident Commander**
- Overall incident coordination
- Communication hub
- Decision authority
- Timeline management

**Technical Lead**
- Root cause analysis
- Technical remediation
- System recovery
- Technical documentation

**Security Lead**
- Security impact assessment
- Containment strategy
- Forensics/evidence preservation
- Compliance requirements

**Compliance Officer**
- Regulatory notification requirements
- Audit/compliance impact
- Documentation requirements
- External communication approval

## Incident Response Procedures

### Certificate Expiration

**Severity**: High (if expired), Medium (30+ days before)

**Detection**:
- Prometheus alert: `ssl_certificate_not_after < 2592000` (30 days)
- PagerDuty notification
- Slack alert

**Response Steps**:

```bash
# 1. Verify certificate status
openssl x509 -in /path/to/cert -noout -checkend 86400

# 2. Determine expiration date
openssl x509 -in /path/to/cert -noout -enddate

# 3. Trigger automated remediation (if available)
bash infrastructure/scripts/incident-response.sh cert-expiry /path/to/cert 30

# 4. Manual renewal if auto-renewal not available
kubectl patch certificate [cert-name] -p '{"spec":{"renewBefore":"720h"}}'

# 5. Verify renewal
kubectl get certificate [cert-name] -o yaml | grep renewalTime

# 6. Restart affected services
kubectl rollout restart deployment/api-gateway -n ecommerce

# 7. Verify service health
kubectl rollout status deployment/api-gateway -n ecommerce

# 8. Document in incident log
cat >> incident-log.txt << EOF
Incident: Certificate Expiration
Service: [service-name]
Date: $(date)
Action: Certificate renewed
Status: Resolved
EOF
```

**Escalation**: If manual renewal fails, escalate to Infrastructure team

### Secrets Compromise

**Severity**: Critical

**Detection**:
- Audit log shows anomalous access
- Automated detection: unusual IP/location
- External report of leaked credentials
- Internal policy violation alert

**Response Steps**:

```bash
# 1. IMMEDIATELY trigger incident response
bash infrastructure/scripts/incident-response.sh secrets-compromise [secret-name] "compromise-detected"

# 2. Automated actions (script performs):
#    - Generate new secret value
#    - Update in Vault
#    - Update in Kubernetes
#    - Restart affected services
#    - Revoke old key

# 3. Verify remediation
vault kv get secret/[secret-name]
kubectl describe secret [secret-name] -n ecommerce

# 4. Check service status
kubectl get pods -n ecommerce

# 5. Monitor logs for errors
kubectl logs -f deployment/[affected-service] -n ecommerce

# 6. Conduct forensics
#    - Check who accessed the secret
#    - When did they access it
#    - From which IP address
#    - What did they do with it

# 7. Notify affected stakeholders
# Prepared notification: See below

# 8. Document incident
cat > incident-[timestamp].json << EOF
{
  "incident_type": "secrets_compromise",
  "secret_name": "[secret-name]",
  "detection_time": "$(date -Iseconds)",
  "root_cause": "[analysis]",
  "remediation_actions": "[list of actions]",
  "status": "resolved"
}
EOF
```

**Notification Template**:

```email
Subject: Security Incident - Secret Rotation Required

Dear [Team],

A security incident was detected involving API key rotation.

INCIDENT DETAILS:
- Type: Secrets Compromise
- Affected Secret: [secret-name]
- Detection Time: [time]
- Current Status: [Contained/Mitigated]

ACTIONS TAKEN:
- Old key rotated and revoked
- New key deployed automatically
- Services restarted with new credentials
- Access logs reviewed for unauthorized use

REQUIRED ACTIONS:
- If you cached the old key, update immediately
- Run integration tests with new credentials
- Report any access issues to security team

NO USER DATA WAS COMPROMISED.

Questions? Contact: security-team@example.com
```

### TLS Handshake Failure

**Severity**: High

**Detection**:
- Client connection failures
- Prometheus alert: certificate validation failure
- Application error logs
- MTLS verification failure

**Response Steps**:

```bash
# 1. Verify TLS configuration
echo | openssl s_client -connect [service]:8443 -showcerts

# 2. Analyze certificate chain
openssl x509 -in /etc/ssl/certs/[cert] -noout -text

# 3. Check for certificate revocation
openssl crl -in /etc/ssl/certs/crl.pem -noout -text

# 4. Verify certificate validity
openssl verify -CAfile /etc/ssl/certs/ca-bundle.crt /etc/ssl/certs/[cert]

# 5. Trigger incident response
bash infrastructure/scripts/incident-response.sh tls-failure [service] "certificate-validation-failure"

# 6. If certificate is self-signed or invalid:
#    - Disable affected service
#    - Mark as unhealthy in load balancer
#    - Deploy valid certificate
#    - Restart service

# 7. Verify recovery
echo | openssl s_client -connect [service]:8443 | grep "Verify return code"

# 8. Monitor application logs
kubectl logs -f deployment/[service] -n ecommerce | grep -i "tls\|ssl\|cert"
```

### Unauthorized Access Attempt

**Severity**: High (repeated attempts), Medium (single attempt)

**Detection**:
- Authentication failure logs
- Failed RBAC authorization
- Unusual access patterns
- Suspicious IP addresses

**Response Steps**:

```bash
# 1. Analyze access pattern
#    - User/service account involved
#    - Resource attempted to access
#    - Timestamp
#    - Source IP address

# 2. Determine intent
#    - Accidental misconfiguration
#    - Privilege escalation attempt
#    - Reconnaissance
#    - Data exfiltration attempt

# 3. Block after multiple attempts (e.g., 5+ failed attempts)
bash infrastructure/scripts/incident-response.sh unauthorized-access [resource] [user] [attempt-count]

# 4. Actions taken:
#    - Revoke user permissions
#    - Update RBAC roles
#    - Block user account
#    - Preserve audit logs

# 5. Investigate root cause
#    - Check recent configuration changes
#    - Review access logs
#    - Interview user
#    - Check for malware/compromise

# 6. Implement preventive measures
#    - Update access control policy
#    - Add additional monitoring
#    - Enhanced logging
#    - User retraining

# 7. Document and communicate
cat > unauthorized-access-[timestamp].txt << EOF
Incident: Unauthorized Access
Date: $(date)
User: [username]
Resource: [resource]
Attempts: [count]
Source IP: [IP]
Actions Taken: [list]
Status: Resolved
EOF
```

### Data Breach

**Severity**: Critical (requires immediate escalation)

**Detection**:
- Unusual data access patterns
- Exfiltration attempt detected
- External report
- Internal audit finding

**Response Steps**:

```bash
# 1. IMMEDIATELY trigger critical incident response
bash infrastructure/scripts/incident-response.sh data-breach [breach-type] [affected-records]

# 2. Containment (automated by script):
#    - Isolate affected systems
#    - Revoke compromised credentials
#    - Block suspicious IP addresses
#    - Enable enhanced logging

# 3. Investigation
#    - Preserve evidence
#    - Determine scope (how many records)
#    - Identify what data was exposed
#    - Determine when breach occurred
#    - Identify how breach occurred

# 4. Notification Process (72-hour GDPR deadline)
#    - Hour 0: Alert internal team
#    - Hour 1: Escalate to DPO
#    - Hour 2: Brief legal team
#    - Hour 24: Notify key stakeholders
#    - Hour 48: Prepare regulatory notification
#    - Hour 72: Notify authorities (if required)
#    - Within 5 business days: Notify individuals (if high risk)

# 5. Regulatory notification
cat > breach-notification-[timestamp].txt << EOF
INCIDENT NOTIFICATION

Incident Type: Data Breach
Date Discovered: $(date)
Data Affected: [description]
Number of Individuals: [count]
Security Measures: [encryption status]
Likely Risk: [High/Medium/Low]

Notification Authorities: [list]
Notification Timeline: [timeline]

Contact Information: [DPO details]
EOF

# 6. Public communication
#    - Press release (if public disclosure required)
#    - Website banner notification
#    - Email to affected individuals
#    - Credit monitoring (if payment card data)
#    - Identity theft protection (if PII exposed)

# 7. Post-breach activities
#    - Root cause analysis
#    - Remediation plan
#    - Enhanced security measures
#    - Employee training
#    - Vendor assessment
#    - Incident report to board/regulators
```

**72-Hour Breach Notification Deadline**:

```
Hour 0:       Breach detected and confirmed
              - Activate incident response team
              - Secure evidence
              - Isolate systems
              
Hour 1-4:     Initial investigation
              - Determine scope
              - Preserve logs
              - Brief stakeholders
              
Hour 4-24:    Detailed analysis
              - Root cause analysis
              - Affected data identification
              - Risk assessment
              - Individual notification method
              
Hour 24-48:   Preparation
              - Draft authority notification
              - Prepare individual notifications
              - Legal review
              - Executive approval
              
Hour 48-72:   Notification
              - Submit to supervisory authority
              - Send notifications to individuals (if applicable)
              - Publish on website
              - Media coordination
              
Hour 72+:     Ongoing
              - Individual support
              - Credit monitoring coordination
              - Regulatory follow-up
              - Root cause remediation
```

## Incident Documentation

### Incident Report Template

```markdown
# Incident Report

**Incident ID**: [unique identifier]
**Date/Time**: [date and time]
**Duration**: [start to resolution time]
**Severity**: [Critical/High/Medium/Low]

## Summary
[Executive summary - 2-3 sentences]

## Impact
- Services Affected: [list]
- Users Impacted: [count]
- Data Affected: [description]
- Financial Impact: [estimate]

## Timeline
| Time | Event |
|------|-------|
| [time] | Event 1 |
| [time] | Event 2 |
| [time] | Resolution |

## Root Cause
[What caused the incident]

## Actions Taken
- Immediate containment: [actions]
- Temporary fix: [actions]
- Permanent fix: [actions]

## Prevention
[How to prevent this in the future]

## Follow-up Actions
- [ ] Action 1 - Owner - Due Date
- [ ] Action 2 - Owner - Due Date

**Prepared by**: [name]
**Reviewed by**: [name]
**Approved by**: [name]
**Date**: [date]
```

## Incident Communication

### Communication Tree

```
Incident Detected
       ↓
  Incident Commander
   ↙      ↓      ↘
Tech Lead Security DPO
   ↓      ↓      ↓
Team A  Team B  Legal
   ↓      ↓      ↓
  Fix  Contain Notify
       ↓
   All-Hands Sync
       ↓
  Stakeholder Updates
```

### Communication Templates

**Internal Alert**:
```
🚨 SECURITY INCIDENT ALERT

Type: [Certificate Expiration/Secrets Compromise/etc]
Severity: [Critical/High]
Status: [Investigating/Contained/Resolved]
Impact: [Services affected]

Action: [What we're doing]
Timeline: [Expected resolution]

Updates: [Slack channel]
```

**Stakeholder Update**:
```
Subject: Security Incident Update - [Incident ID]

Status: [Ongoing/Contained/Resolved]
Progress: [What's been done]
Current Actions: [What's happening now]
Estimated Resolution: [Time]
Next Update: [Time]

Questions: Contact security-team@example.com
```

**All-Clear Message**:
```
INCIDENT RESOLVED - [Incident ID]

The security incident has been contained and resolved.

Impact Summary:
- Duration: [time]
- Services Affected: [services]
- Cause: [root cause]

Actions Taken:
[list of remediation]

Prevention:
[preventive measures implemented]

Incident Report: [link to full report]

Thank you for your patience.
```

## Recovery Procedures

### Service Recovery

```bash
# 1. Verify services are healthy
kubectl get pods -n ecommerce

# 2. Check service logs for errors
kubectl logs -f deployment/[service] -n ecommerce

# 3. Run health checks
curl -s http://[service]:8080/health

# 4. Verify data integrity
# [Custom validation commands]

# 5. Re-enable traffic
kubectl scale deployment/[service] --replicas=3 -n ecommerce

# 6. Monitor for issues
kubectl top pods -n ecommerce
```

### Data Verification

```bash
# 1. Check database integrity
# [Database-specific check]

# 2. Verify backups
ls -la /backups/latest-backup/

# 3. Test disaster recovery
# [Run DR test procedures]

# 4. Validate data consistency
# [Cross-system verification]
```

## Post-Incident Review

**Schedule**: Within 5 business days of incident resolution

**Attendees**: 
- Incident Commander
- Technical Lead
- Security Lead
- DPO (if data breach)
- Relevant team leads

**Agenda**:
1. Incident summary and timeline
2. What went well
3. What needs improvement
4. Action items and owners
5. Training needs
6. Documentation updates

**Deliverable**: Post-Incident Review document with:
- Timeline of events
- Root cause analysis
- Preventive measures
- Action items with owners and due dates

## Incident Prevention

### Monitoring

- Certificate expiration: Daily checks 30 days before expiry
- TLS health: Real-time monitoring of handshake failures
- Secrets access: Continuous audit logging review
- Access patterns: Weekly anomaly detection

### Regular Drills

```bash
# Monthly incident response drills
# Schedule: First Friday of each month

# Drill scenarios:
# 1. Certificate expiration response
# 2. Secrets compromise response
# 3. TLS failure response
# 4. Data breach notification

# Evaluation criteria:
# - Time to detect (< 5 minutes)
# - Time to respond (< 30 minutes)
# - Time to resolve (< 2 hours)
# - Communication effectiveness
```

### Continuous Improvement

- Review incidents quarterly for patterns
- Update response procedures based on learnings
- Enhance monitoring based on past incidents
- Conduct annual incident response training

## Resources

- Incident response scripts: `infrastructure/scripts/incident-response.sh`
- Monitoring dashboards: Grafana at `http://localhost:3000`
- Audit logs: Kibana at `http://localhost:5601`
- Compliance checklists: `PCI_DSS_CHECKLIST.md`, `GDPR_CHECKLIST.md`
- Security audit procedures: `SECURITY_AUDIT.md`

## Contacts

- **Security Team**: security-team@example.com
- **On-Call**: [PagerDuty]
- **Compliance**: compliance@example.com
- **Executive Escalation**: ciso@example.com
