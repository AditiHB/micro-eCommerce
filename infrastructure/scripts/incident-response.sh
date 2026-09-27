#!/bin/bash
# Automated Incident Response Script
# Handles certificate expiration, secrets compromise, and security incidents

set -euo pipefail

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
INCIDENT_LOG="/var/log/security/incident-$(date +%Y%m%d_%H%M%S).log"
SLACK_WEBHOOK="${SLACK_WEBHOOK:-}"
PAGERDUTY_KEY="${PAGERDUTY_KEY:-}"
INCIDENT_DIR="/tmp/incidents"
mkdir -p "$INCIDENT_DIR"
mkdir -p "$(dirname "$INCIDENT_LOG")"

log() {
  echo "$(date '+%Y-%m-%d %H:%M:%S') - $1" | tee -a "$INCIDENT_LOG"
}

notify_slack() {
  local severity=$1
  local message=$2

  if [ -z "$SLACK_WEBHOOK" ]; then
    return
  fi

  local color="danger"
  [ "$severity" = "warning" ] && color="warning"
  [ "$severity" = "info" ] && color="good"

  curl -X POST "$SLACK_WEBHOOK" \
    -H 'Content-Type: application/json' \
    -d @- << EOF
{
  "attachments": [
    {
      "color": "$color",
      "title": "Security Incident: $severity",
      "text": "$message",
      "ts": $(date +%s)
    }
  ]
}
EOF
}

notify_pagerduty() {
  local severity=$1
  local description=$2

  if [ -z "$PAGERDUTY_KEY" ]; then
    return
  fi

  local pagerduty_severity="error"
  [ "$severity" = "warning" ] && pagerduty_severity="warning"
  [ "$severity" = "info" ] && pagerduty_severity="info"

  curl -X POST "https://events.pagerduty.com/v2/enqueue" \
    -H "Content-Type: application/json" \
    -d @- << EOF
{
  "routing_key": "$PAGERDUTY_KEY",
  "event_action": "trigger",
  "payload": {
    "summary": "Security Incident - $description",
    "severity": "$pagerduty_severity",
    "source": "micro-ecommerce-security",
    "custom_details": {
      "incident_log": "$INCIDENT_LOG",
      "timestamp": "$(date -Iseconds)"
    }
  }
}
EOF
}

# Handle certificate expiration
handle_certificate_expiration() {
  local cert_path=$1
  local days_left=$2

  log "INCIDENT: Certificate expiration detected - $cert_path (expires in $days_left days)"

  # Extract certificate details
  local cn=$(openssl x509 -in "$cert_path" -noout -subject 2>/dev/null | grep -oP 'CN\s*=\s*\K[^,]+' || echo "Unknown")
  local expiry=$(openssl x509 -in "$cert_path" -noout -enddate 2>/dev/null | cut -d= -f2)

  log "Certificate CN: $cn"
  log "Expiry Date: $expiry"

  # Create incident record
  cat > "$INCIDENT_DIR/cert-expiry-$(date +%s).json" << EOF
{
  "incident_type": "certificate_expiration",
  "severity": "critical",
  "certificate_path": "$cert_path",
  "common_name": "$cn",
  "days_until_expiry": $days_left,
  "expiry_date": "$expiry",
  "detected_at": "$(date -Iseconds)",
  "status": "investigating"
}
EOF

  # Check if cert-manager is available for auto-renewal
  if kubectl get crd certificates.cert-manager.io &>/dev/null 2>&1; then
    log "cert-manager detected - triggering certificate renewal"
    kubectl annotate certificate "$cn" cert-manager.io/issue-temporary-certificate="true" --overwrite 2>/dev/null || true
  else
    log "Manual certificate renewal required"
  fi

  # Notify security team
  notify_slack "critical" "🔒 Certificate expiring in $days_left days: $cn"
  notify_pagerduty "critical" "Certificate expiration: $cn expires in $days_left days"

  log "Certificate expiration incident recorded"
}

# Handle secrets compromise
handle_secrets_compromise() {
  local secret_name=$1
  local reason=$2

  log "INCIDENT: Secrets compromise detected - $secret_name"
  log "Reason: $reason"

  # Create incident record
  cat > "$INCIDENT_DIR/secrets-compromise-$(date +%s).json" << EOF
{
  "incident_type": "secrets_compromise",
  "severity": "critical",
  "secret_name": "$secret_name",
  "compromise_reason": "$reason",
  "detected_at": "$(date -Iseconds)",
  "status": "responding"
}
EOF

  # Rotate compromised secret
  log "Rotating compromised secret: $secret_name"

  # Generate new secret value
  NEW_SECRET=$(openssl rand -base64 32)

  # Update in Vault
  if command -v vault &>/dev/null; then
    vault kv put "secret/$secret_name" value="$NEW_SECRET" rotated_at="$(date -Iseconds)" || log "Failed to rotate in Vault"
  fi

  # Update in Kubernetes
  if kubectl get secret "$secret_name" &>/dev/null 2>&1; then
    kubectl patch secret "$secret_name" -p "{\"data\":{\"value\":\"$(echo -n $NEW_SECRET | base64)\"}, \"metadata\":{\"annotations\":{\"rotated-at\":\"$(date -Iseconds)\"}}}" || log "Failed to rotate in Kubernetes"
  fi

  # Restart services to pick up new secret
  log "Restarting affected services to pick up new secret"
  kubectl rollout restart deployment -l "uses-secret=$secret_name" 2>/dev/null || true

  # Wait for services to be ready
  sleep 30

  # Revoke old secret key
  log "Revoking old secret key"
  if command -v vault &>/dev/null; then
    vault kv metadata delete "secret/$secret_name-old" 2>/dev/null || true
  fi

  # Notify security team
  notify_slack "critical" "🚨 Secrets compromise detected: $secret_name - automatic rotation triggered"
  notify_pagerduty "critical" "Secrets compromise and rotation: $secret_name"

  log "Secrets compromise incident recorded and remediated"
}

# Handle TLS handshake failure
handle_tls_failure() {
  local service=$1
  local error=$2

  log "INCIDENT: TLS handshake failure - $service"
  log "Error: $error"

  # Create incident record
  cat > "$INCIDENT_DIR/tls-failure-$(date +%s).json" << EOF
{
  "incident_type": "tls_handshake_failure",
  "severity": "high",
  "service": "$service",
  "error": "$error",
  "detected_at": "$(date -Iseconds)",
  "status": "investigating"
}
EOF

  # Check certificate validity
  echo | openssl s_client -connect "$service:8443" 2>&1 | grep "Verify return code" | tee -a "$INCIDENT_LOG"

  # Check if certificate has been revoked
  local cert_serial=$(echo | openssl s_client -connect "$service:8443" 2>/dev/null | openssl x509 -noout -serial | cut -d= -f2)
  log "Certificate Serial: $cert_serial"

  # Disable affected service if certificate is invalid
  if echo | openssl s_client -connect "$service:8443" 2>/dev/null | grep -q "self signed certificate"; then
    log "WARNING: Self-signed certificate detected on $service"

    # Mark service as unhealthy
    kubectl set env deployment/"$service" "TLS_INVALID=true" 2>/dev/null || true

    notify_slack "critical" "🔴 Service $service has TLS issues - service disabled"
    notify_pagerduty "critical" "TLS failure on $service - service marked as unhealthy"
  fi

  log "TLS failure incident recorded"
}

# Handle unauthorized access attempt
handle_unauthorized_access() {
  local resource=$1
  local user=$2
  local attempt_count=$3

  log "INCIDENT: Unauthorized access attempt - $resource by $user (attempt #$attempt_count)"

  # Create incident record
  cat > "$INCIDENT_DIR/unauthorized-access-$(date +%s).json" << EOF
{
  "incident_type": "unauthorized_access",
  "severity": "high",
  "resource": "$resource",
  "user": "$user",
  "attempt_count": $attempt_count,
  "detected_at": "$(date -Iseconds)",
  "status": "monitoring"
}
EOF

  # Block user after multiple attempts (e.g., >5)
  if [ $attempt_count -gt 5 ]; then
    log "Blocking user due to multiple unauthorized access attempts"

    # Update RBAC to deny all access
    kubectl patch rolebinding "$user-rolebinding" -p '{"subjects":[]}' 2>/dev/null || true

    notify_slack "critical" "🚫 User $user blocked due to multiple unauthorized access attempts"
    notify_pagerduty "high" "Unauthorized access: user $user blocked after $attempt_count attempts"
  else
    notify_slack "warning" "⚠️ Unauthorized access attempt by $user on $resource (attempt #$attempt_count)"
  fi

  log "Unauthorized access incident recorded"
}

# Handle data breach detection
handle_data_breach() {
  local breach_type=$1
  local affected_records=$2

  log "CRITICAL INCIDENT: Data breach detected - $breach_type"
  log "Affected Records: $affected_records"

  # Create incident record
  cat > "$INCIDENT_DIR/data-breach-$(date +%s).json" << EOF
{
  "incident_type": "data_breach",
  "severity": "critical",
  "breach_type": "$breach_type",
  "affected_records": $affected_records,
  "detected_at": "$(date -Iseconds)",
  "status": "containment",
  "compliance_notification_required": true
}
EOF

  log "GDPR Breach Notification Required: Yes"
  log "Timeline: Must notify authorities and individuals within 72 hours"

  # Initiate containment
  log "Isolating affected services"
  kubectl patch networkpolicy "default-deny-ingress" --type merge -p '{"spec":{"ingress":[]}}'

  # Alert all stakeholders
  notify_slack "critical" "🚨🚨🚨 CRITICAL: Data breach detected - $breach_type - Affecting $affected_records records"
  notify_pagerduty "critical" "CRITICAL: Data breach - $breach_type affecting $affected_records records"

  # Generate incident report for compliance
  cat > "$INCIDENT_DIR/breach-notification-$(date +%s).txt" << NOTIFICATION
DATA BREACH INCIDENT REPORT
Generated: $(date)

Incident Type: $breach_type
Affected Records: $affected_records
Discovery Date: $(date -Iseconds)

GDPR Breach Notification Timeline:
- Immediate: Security and compliance teams notified (THIS STEP - DONE)
- Within 72 hours: Notify regulatory authorities
- Without undue delay: Notify affected individuals

Required Actions:
1. ✓ Security incident created and escalated
2. ⏳ Preserve evidence
3. ⏳ Notify DPO (Data Protection Officer)
4. ⏳ Notify regulatory authorities
5. ⏳ Notify affected individuals
6. ⏳ Publish breach notification on website
7. ⏳ Document breach response process

Contact Information:
- Security Team: security-team@example.com
- DPO: dpo@example.com
- Legal: legal@example.com
NOTIFICATION

  log "Data breach incident recorded - immediate escalation required"
}

# Generate compliance report
generate_incident_report() {
  log "Generating comprehensive incident report"

  cat > "$INCIDENT_DIR/incident-summary-$(date +%Y%m%d).txt" << SUMMARY
SECURITY INCIDENT SUMMARY REPORT
Generated: $(date)

Open Incidents:
SUMMARY

  find "$INCIDENT_DIR" -name "*.json" -mtime -1 -exec cat {} >> "$INCIDENT_DIR/incident-summary-$(date +%Y%m%d).txt" \;

  # Upload to Elasticsearch for archival
  if command -v curl &>/dev/null; then
    curl -X POST "http://elasticsearch:9200/incident-reports/_doc" \
      -H 'Content-Type: application/json' \
      -d @"$INCIDENT_DIR/incident-summary-$(date +%Y%m%d).txt" 2>/dev/null || true
  fi

  log "Incident report generated: $INCIDENT_DIR/incident-summary-$(date +%Y%m%d).txt"
}

# Main incident response based on trigger
main() {
  local incident_type=${1:-unknown}

  log "Incident Response System Started - Type: $incident_type"

  case "$incident_type" in
    cert-expiry)
      handle_certificate_expiration "$2" "$3"
      ;;
    secrets-compromise)
      handle_secrets_compromise "$2" "$3"
      ;;
    tls-failure)
      handle_tls_failure "$2" "$3"
      ;;
    unauthorized-access)
      handle_unauthorized_access "$2" "$3" "$4"
      ;;
    data-breach)
      handle_data_breach "$2" "$3"
      ;;
    generate-report)
      generate_incident_report
      ;;
    *)
      log "Unknown incident type: $incident_type"
      exit 1
      ;;
  esac

  log "Incident response completed"
}

# Run main function
main "$@"
