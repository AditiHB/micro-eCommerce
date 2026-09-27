#!/bin/bash

# SSL/TLS Testing Script
# Validates SSL/TLS configuration, certificate validity, and security headers

set -e

HOST="${HOST:-localhost}"
PORT="${PORT:-443}"
CERT_FILE="${CERT_FILE:-./infrastructure/nginx/certs/cert.pem}"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

PASSED=0
FAILED=0

# Test functions
test_passed() {
    echo -e "${GREEN}✓ PASSED${NC}: $1"
    ((PASSED++))
}

test_failed() {
    echo -e "${RED}✗ FAILED${NC}: $1"
    ((FAILED++))
}

test_info() {
    echo -e "${BLUE}ℹ INFO${NC}: $1"
}

test_warn() {
    echo -e "${YELLOW}⚠ WARNING${NC}: $1"
}

echo "=========================================="
echo "SSL/TLS Configuration Testing"
echo "=========================================="
echo "Target: $HOST:$PORT"
echo ""

# Test 1: Certificate file exists
echo "Test 1: Certificate File Validation"
echo "---"
if [ -f "$CERT_FILE" ]; then
    test_passed "Certificate file exists: $CERT_FILE"
else
    test_failed "Certificate file not found: $CERT_FILE"
fi
echo ""

# Test 2: Certificate validity
echo "Test 2: Certificate Validity"
echo "---"
if [ -f "$CERT_FILE" ]; then
    EXPIRY_DATE=$(openssl x509 -enddate -noout -in "$CERT_FILE" | cut -d= -f2)
    EXPIRY_EPOCH=$(date -d "$EXPIRY_DATE" +%s)
    NOW_EPOCH=$(date +%s)
    DAYS_UNTIL_EXPIRY=$(( ($EXPIRY_EPOCH - $NOW_EPOCH) / 86400 ))

    if [ $DAYS_UNTIL_EXPIRY -gt 0 ]; then
        test_passed "Certificate is valid for $DAYS_UNTIL_EXPIRY more days (expires: $EXPIRY_DATE)"
    else
        test_failed "Certificate has expired: $EXPIRY_DATE"
    fi

    if [ $DAYS_UNTIL_EXPIRY -lt 30 ]; then
        test_warn "Certificate expires within 30 days"
    fi
else
    test_failed "Cannot check certificate validity (file not found)"
fi
echo ""

# Test 3: Certificate subject and issuer
echo "Test 3: Certificate Details"
echo "---"
if [ -f "$CERT_FILE" ]; then
    SUBJECT=$(openssl x509 -subject -noout -in "$CERT_FILE" | sed 's/subject=//')
    ISSUER=$(openssl x509 -issuer -noout -in "$CERT_FILE" | sed 's/issuer=//')
    test_info "Subject: $SUBJECT"
    test_info "Issuer: $ISSUER"
    test_passed "Certificate details extracted"
else
    test_failed "Cannot extract certificate details (file not found)"
fi
echo ""

# Test 4: SSL connection test
echo "Test 4: SSL/TLS Connection Test"
echo "---"
if timeout 10 openssl s_client -connect "$HOST:$PORT" -servername "$HOST" </dev/null 2>/dev/null | grep -q "Verify return code"; then
    test_passed "Successfully established SSL connection to $HOST:$PORT"
else
    test_failed "Failed to establish SSL connection to $HOST:$PORT"
fi
echo ""

# Test 5: TLS version check
echo "Test 5: TLS Version Support"
echo "---"
TLS_VERSIONS=("TLSv1_2" "TLSv1_3")
for VERSION in "${TLS_VERSIONS[@]}"; do
    if timeout 10 openssl s_client -connect "$HOST:$PORT" -servername "$HOST" -"$VERSION" </dev/null 2>&1 | grep -q "SSL-Session"; then
        test_passed "$VERSION is supported"
    else
        test_warn "$VERSION is not supported or connection failed"
    fi
done

# Test that TLS 1.0 and 1.1 are NOT supported
echo ""
test_info "Verifying weak TLS versions are disabled..."
if timeout 10 openssl s_client -connect "$HOST:$PORT" -servername "$HOST" -tls1 </dev/null 2>&1 | grep -q "sslv3 alert handshake failure\|SSLV3_ALERT_HANDSHAKE_FAILURE"; then
    test_passed "TLSv1.0 is properly disabled"
else
    test_warn "TLSv1.0 status unclear (may still need verification)"
fi

if timeout 10 openssl s_client -connect "$HOST:$PORT" -servername "$HOST" -tls1_1 </dev/null 2>&1 | grep -q "sslv3 alert handshake failure\|SSLV3_ALERT_HANDSHAKE_FAILURE"; then
    test_passed "TLSv1.1 is properly disabled"
else
    test_warn "TLSv1.1 status unclear (may still need verification)"
fi
echo ""

# Test 6: Security Headers
echo "Test 6: Security Headers Validation"
echo "---"
HEADERS=$(curl -k -s -I "https://$HOST:$PORT/health" 2>/dev/null)

EXPECTED_HEADERS=(
    "Strict-Transport-Security"
    "X-Frame-Options"
    "X-Content-Type-Options"
    "X-XSS-Protection"
)

for HEADER in "${EXPECTED_HEADERS[@]}"; do
    if echo "$HEADERS" | grep -q "$HEADER"; then
        test_passed "Security header present: $HEADER"
    else
        test_failed "Missing security header: $HEADER"
    fi
done
echo ""

# Test 7: Perfect Forward Secrecy
echo "Test 7: Perfect Forward Secrecy (PFS)"
echo "---"
if timeout 10 openssl s_client -connect "$HOST:$PORT" -servername "$HOST" </dev/null 2>&1 | grep -q "Server Temp Key"; then
    test_passed "Perfect Forward Secrecy is enabled (Temp Key present)"
else
    test_warn "Could not confirm PFS status"
fi
echo ""

# Test 8: Certificate chain
echo "Test 8: Certificate Chain"
echo "---"
CHAIN_LENGTH=$(timeout 10 openssl s_client -connect "$HOST:$PORT" -servername "$HOST" </dev/null 2>&1 | grep -c "depth=")
if [ $CHAIN_LENGTH -gt 0 ]; then
    test_passed "Certificate chain length: $CHAIN_LENGTH"
else
    test_warn "Could not verify certificate chain"
fi
echo ""

# Test 9: Cipher strength
echo "Test 9: Cipher Strength Check"
echo "---"
CIPHER=$(timeout 10 openssl s_client -connect "$HOST:$PORT" -servername "$HOST" </dev/null 2>&1 | grep "Cipher" | awk -F': ' '{print $2}')
if [ -n "$CIPHER" ]; then
    test_info "Current cipher: $CIPHER"
    if echo "$CIPHER" | grep -q "ECDHE\|CHACHA"; then
        test_passed "Strong cipher suite detected"
    else
        test_warn "Cipher suite may not be optimal: $CIPHER"
    fi
else
    test_warn "Could not determine cipher suite"
fi
echo ""

# Summary
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo -e "${GREEN}Passed: $PASSED${NC}"
echo -e "${RED}Failed: $FAILED${NC}"

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}Some tests failed. Please review the output above.${NC}"
    exit 1
fi
