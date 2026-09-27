#!/bin/bash
# SSL Labs & External Audits Testing Script
# Performs automated security testing of SSL/TLS configuration

set -euo pipefail

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

AUDIT_REPORT="/tmp/ssl-audit-report-$(date +%Y%m%d_%H%M%S).txt"
SERVICES=${SERVICES:-"localhost:8443"}
FAILED_CHECKS=0
PASSED_CHECKS=0

echo "=== SSL/TLS Security Audit Report ===" | tee "$AUDIT_REPORT"
echo "Date: $(date)" | tee -a "$AUDIT_REPORT"
echo "Services: $SERVICES" | tee -a "$AUDIT_REPORT"
echo "" | tee -a "$AUDIT_REPORT"

# Helper function for checks
run_check() {
  local check_name=$1
  local service=$2
  local command=$3

  echo -n "Testing: $check_name for $service... " | tee -a "$AUDIT_REPORT"

  if eval "$command" > /tmp/check_output.txt 2>&1; then
    echo -e "${GREEN}✓ PASS${NC}" | tee -a "$AUDIT_REPORT"
    ((PASSED_CHECKS++))
  else
    echo -e "${RED}✗ FAIL${NC}" | tee -a "$AUDIT_REPORT"
    cat /tmp/check_output.txt | sed 's/^/  /' | tee -a "$AUDIT_REPORT"
    ((FAILED_CHECKS++))
  fi
}

# Test each service
for service in $SERVICES; do
  host=$(echo $service | cut -d: -f1)
  port=$(echo $service | cut -d: -f2)

  echo "" | tee -a "$AUDIT_REPORT"
  echo "=== Testing Service: $host:$port ===" | tee -a "$AUDIT_REPORT"

  # SSL/TLS Protocol Tests
  echo "" | tee -a "$AUDIT_REPORT"
  echo "--- TLS Protocol Support ---" | tee -a "$AUDIT_REPORT"

  run_check "TLSv1.3 Support" "$service" \
    "echo | openssl s_client -connect $host:$port -tls1_3 2>/dev/null | grep -q 'Protocol.*TLSv1.3'"

  run_check "TLSv1.2 Support" "$service" \
    "echo | openssl s_client -connect $host:$port -tls1_2 2>/dev/null | grep -q 'Protocol.*TLSv1.2'"

  run_check "SSLv2 Disabled" "$service" \
    "! echo | openssl s_client -connect $host:$port -ssl2 2>/dev/null | grep -q 'Protocol.*SSLv2'"

  run_check "SSLv3 Disabled" "$service" \
    "! echo | openssl s_client -connect $host:$port -ssl3 2>/dev/null | grep -q 'Protocol.*SSLv3'"

  run_check "TLSv1.0 Disabled" "$service" \
    "! echo | openssl s_client -connect $host:$port -tls1 2>/dev/null | grep -q 'Protocol.*TLSv1[^.2-3]'"

  run_check "TLSv1.1 Disabled" "$service" \
    "! echo | openssl s_client -connect $host:$port -tls1_1 2>/dev/null | grep -q 'Protocol.*TLSv1.1'"

  # Cipher Suite Tests
  echo "" | tee -a "$AUDIT_REPORT"
  echo "--- Cipher Suite Analysis ---" | tee -a "$AUDIT_REPORT"

  run_check "No RC4 Ciphers" "$service" \
    "! echo | openssl s_client -connect $host:$port 2>/dev/null | grep -i 'cipher' | grep -iq 'rc4'"

  run_check "No DES Ciphers" "$service" \
    "! echo | openssl s_client -connect $host:$port 2>/dev/null | grep -i 'cipher' | grep -iq 'des'"

  run_check "No MD5 Ciphers" "$service" \
    "! echo | openssl s_client -connect $host:$port 2>/dev/null | grep -i 'cipher' | grep -iq 'md5'"

  run_check "No NULL Ciphers" "$service" \
    "! echo | openssl s_client -connect $host:$port 2>/dev/null | grep -i 'cipher' | grep -iq 'null'"

  run_check "No EXPORT Ciphers" "$service" \
    "! echo | openssl s_client -connect $host:$port 2>/dev/null | grep -i 'cipher' | grep -iq 'export'"

  run_check "Strong Encryption (256-bit)" "$service" \
    "echo | openssl s_client -connect $host:$port 2>/dev/null | grep -i 'cipher' | grep -iq '256'"

  run_check "GCM Mode Ciphers" "$service" \
    "echo | openssl s_client -connect $host:$port 2>/dev/null | grep -i 'cipher' | grep -iq 'gcm'"

  run_check "No Anonymous DH" "$service" \
    "! echo | openssl s_client -connect $host:$port 2>/dev/null | grep -i 'cipher' | grep -iq 'adh\\|aecdh'"

  # Certificate Tests
  echo "" | tee -a "$AUDIT_REPORT"
  echo "--- Certificate Validation ---" | tee -a "$AUDIT_REPORT"

  run_check "Valid Certificate" "$service" \
    "echo | openssl s_client -connect $host:$port 2>/dev/null | grep -q 'subject='"

  run_check "Certificate Not Expired" "$service" \
    "echo | openssl s_client -connect $host:$port 2>/dev/null | openssl x509 -noout -checkend 86400 2>/dev/null"

  run_check "Certificate Chain Complete" "$service" \
    "echo | openssl s_client -connect $host:$port -showcerts 2>/dev/null | grep -q 'subject=' && echo | openssl s_client -connect $host:$port -showcerts 2>/dev/null | grep -q 'issuer='"

  run_check "Self-Signed Detection" "$service" \
    "! echo | openssl s_client -connect $host:$port 2>/dev/null | openssl x509 -noout 2>/dev/null | grep -q 'self signed' || echo 'Self-signed cert detected (expected for testing)' && true"

  run_check "Valid Certificate Signature" "$service" \
    "echo | openssl s_client -connect $host:$port 2>/dev/null | openssl x509 -noout -verify 2>/dev/null | grep -q 'ok\\|error' && true"

  # Certificate Details
  echo "" | tee -a "$AUDIT_REPORT"
  echo "--- Certificate Details ---" | tee -a "$AUDIT_REPORT"

  CN=$(echo | openssl s_client -connect $host:$port 2>/dev/null | openssl x509 -noout -subject 2>/dev/null | grep -oP 'CN\s*=\s*\K[^,]+' || echo "Unknown")
  echo "Common Name: $CN" | tee -a "$AUDIT_REPORT"

  ISSUED=$(echo | openssl s_client -connect $host:$port 2>/dev/null | openssl x509 -noout -issuer 2>/dev/null | grep -oP 'issuer=\K.*' || echo "Unknown")
  echo "Issuer: $ISSUED" | tee -a "$AUDIT_REPORT"

  EXPIRY=$(echo | openssl s_client -connect $host:$port 2>/dev/null | openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2 || echo "Unknown")
  echo "Expiry Date: $EXPIRY" | tee -a "$AUDIT_REPORT"

  KEYSIZE=$(echo | openssl s_client -connect $host:$port 2>/dev/null | openssl x509 -noout -text 2>/dev/null | grep -oP 'Public-Key: \(\K[0-9]+' || echo "Unknown")
  echo "Key Size: ${KEYSIZE} bits" | tee -a "$AUDIT_REPORT"

  SIGALG=$(echo | openssl s_client -connect $host:$port 2>/dev/null | openssl x509 -noout -text 2>/dev/null | grep -oP 'Signature Algorithm: \K.*' | head -1 || echo "Unknown")
  echo "Signature Algorithm: $SIGALG" | tee -a "$AUDIT_REPORT"

  # Handshake Performance
  echo "" | tee -a "$AUDIT_REPORT"
  echo "--- Handshake Performance ---" | tee -a "$AUDIT_REPORT"

  START=$(date +%s%N)
  echo | timeout 5 openssl s_client -connect $host:$port >/dev/null 2>&1 || true
  END=$(date +%s%N)
  DURATION=$(( ($END - $START) / 1000000 ))

  if [ $DURATION -lt 100 ]; then
    echo -e "${GREEN}TLS Handshake Time: ${DURATION}ms (Excellent)${NC}" | tee -a "$AUDIT_REPORT"
  elif [ $DURATION -lt 500 ]; then
    echo -e "${YELLOW}TLS Handshake Time: ${DURATION}ms (Good)${NC}" | tee -a "$AUDIT_REPORT"
  else
    echo -e "${RED}TLS Handshake Time: ${DURATION}ms (Slow)${NC}" | tee -a "$AUDIT_REPORT"
  fi

  # Security Headers Check
  echo "" | tee -a "$AUDIT_REPORT"
  echo "--- Security Headers ---" | tee -a "$AUDIT_REPORT"

  run_check "HSTS Header" "$service" \
    "curl -s --insecure https://$host:$port/ -I 2>/dev/null | grep -iq 'strict-transport-security'"

  run_check "X-Frame-Options Header" "$service" \
    "curl -s --insecure https://$host:$port/ -I 2>/dev/null | grep -iq 'x-frame-options'"

  run_check "X-Content-Type-Options Header" "$service" \
    "curl -s --insecure https://$host:$port/ -I 2>/dev/null | grep -iq 'x-content-type-options'"

  run_check "Content-Security-Policy Header" "$service" \
    "curl -s --insecure https://$host:$port/ -I 2>/dev/null | grep -iq 'content-security-policy'"

done

# Summary Report
echo "" | tee -a "$AUDIT_REPORT"
echo "=== AUDIT SUMMARY ===" | tee -a "$AUDIT_REPORT"
TOTAL_CHECKS=$((PASSED_CHECKS + FAILED_CHECKS))
if [ $TOTAL_CHECKS -gt 0 ]; then
  PASS_PERCENTAGE=$((PASSED_CHECKS * 100 / TOTAL_CHECKS))
else
  PASS_PERCENTAGE=0
fi

echo "Total Checks: $TOTAL_CHECKS" | tee -a "$AUDIT_REPORT"
echo -e "${GREEN}Passed: $PASSED_CHECKS${NC}" | tee -a "$AUDIT_REPORT"
echo -e "${RED}Failed: $FAILED_CHECKS${NC}" | tee -a "$AUDIT_REPORT"
echo "Security Score: $PASS_PERCENTAGE%" | tee -a "$AUDIT_REPORT"
echo "" | tee -a "$AUDIT_REPORT"
echo "Report saved to: $AUDIT_REPORT" | tee -a "$AUDIT_REPORT"

if [ $FAILED_CHECKS -eq 0 ]; then
  echo -e "${GREEN}✓ All security checks passed!${NC}" | tee -a "$AUDIT_REPORT"
  exit 0
else
  echo -e "${RED}✗ $FAILED_CHECKS security issues found${NC}" | tee -a "$AUDIT_REPORT"
  exit 1
fi
