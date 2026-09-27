#!/bin/bash

###############################################
# mTLS Connectivity Testing Script
# Tests service-to-service communication with
# mutual TLS certificates
###############################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
NAMESPACE=${NAMESPACE:-ecommerce}
CERT_PATH=${CERT_PATH:-/etc/certs}
CURL_TIMEOUT=${CURL_TIMEOUT:-30}

# Service definitions
declare -A SERVICES=(
    [customer-service]="customer-service.${NAMESPACE}.svc.cluster.local:8081"
    [order-service]="order-service.${NAMESPACE}.svc.cluster.local:8083"
    [inventory-service]="inventory-service.${NAMESPACE}.svc.cluster.local:8082"
    [payment-service]="payment-service.${NAMESPACE}.svc.cluster.local:8084"
    [api-gateway]="api-gateway.${NAMESPACE}.svc.cluster.local:8080"
)

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if cert files exist
check_certificate_files() {
    local service=$1
    local cert_dir="${CERT_PATH}/${service}"

    if [[ ! -f "${cert_dir}/tls.crt" ]]; then
        log_error "Certificate file not found: ${cert_dir}/tls.crt"
        return 1
    fi

    if [[ ! -f "${cert_dir}/tls.key" ]]; then
        log_error "Key file not found: ${cert_dir}/tls.key"
        return 1
    fi

    if [[ ! -f "${cert_dir}/ca.crt" ]]; then
        log_error "CA certificate not found: ${cert_dir}/ca.crt"
        return 1
    fi

    return 0
}

# Get certificate details
get_certificate_info() {
    local cert_file=$1
    echo "Certificate Info for: $cert_file"
    openssl x509 -in "$cert_file" -text -noout | grep -E "Subject:|Issuer:|Not Before|Not After|Public-Key:|CN="
}

# Check certificate validity
check_certificate_validity() {
    local service=$1
    local cert_dir="${CERT_PATH}/${service}"
    local cert_file="${cert_dir}/tls.crt"

    if [[ ! -f "$cert_file" ]]; then
        log_error "Certificate file not found: $cert_file"
        return 1
    fi

    log_info "Checking certificate validity for ${service}..."

    # Check if certificate is expired
    if ! openssl x509 -in "$cert_file" -noout -checkend 0 > /dev/null 2>&1; then
        log_error "Certificate for ${service} has expired!"
        return 1
    fi

    # Check expiration date
    local expiry_date=$(openssl x509 -in "$cert_file" -noout -enddate | cut -d= -f2)
    log_info "Certificate for ${service} expires on: $expiry_date"

    # Check if expiring soon (within 30 days)
    local expiry_epoch=$(date -d "$expiry_date" +%s)
    local current_epoch=$(date +%s)
    local days_until_expiry=$(( (expiry_epoch - current_epoch) / 86400 ))

    if (( days_until_expiry < 30 )); then
        log_warning "Certificate for ${service} will expire in $days_until_expiry days!"
    fi

    log_success "Certificate for ${service} is valid"
    return 0
}

# Test service connectivity
test_service_connectivity() {
    local service=$1
    local endpoint=${SERVICES[$service]}
    local cert_dir="${CERT_PATH}/${service}"

    log_info "Testing mTLS connectivity to ${service} (${endpoint})..."

    if ! check_certificate_files "$service"; then
        log_error "Failed to find certificate files for ${service}"
        return 1
    fi

    # Test with mTLS
    if curl -s --max-time $CURL_TIMEOUT \
        --cacert "${cert_dir}/ca.crt" \
        --cert "${cert_dir}/tls.crt" \
        --key "${cert_dir}/tls.key" \
        "https://${endpoint}/actuator/health" > /dev/null 2>&1; then
        log_success "mTLS connectivity to ${service} successful"
        return 0
    else
        log_error "mTLS connectivity to ${service} failed"
        return 1
    fi
}

# Test certificate chain validation
test_certificate_chain() {
    local service=$1
    local cert_dir="${CERT_PATH}/${service}"

    log_info "Testing certificate chain validation for ${service}..."

    if ! openssl verify -CAfile "${cert_dir}/ca.crt" "${cert_dir}/tls.crt" > /dev/null 2>&1; then
        log_error "Certificate chain validation failed for ${service}"
        return 1
    fi

    log_success "Certificate chain validation successful for ${service}"
    return 0
}

# Test inter-service communication
test_inter_service_communication() {
    log_info "Testing inter-service communication (Order Service -> Inventory Service)..."

    # This test assumes Order Service pod can access the Inventory Service
    local cert_dir="${CERT_PATH}/order-service"
    local endpoint="https://inventory-service.${NAMESPACE}.svc.cluster.local:8082"

    if curl -s --max-time $CURL_TIMEOUT \
        --cacert "${cert_dir}/ca.crt" \
        --cert "${cert_dir}/tls.crt" \
        --key "${cert_dir}/tls.key" \
        "${endpoint}/actuator/health" > /dev/null 2>&1; then
        log_success "Inter-service mTLS communication successful"
        return 0
    else
        log_error "Inter-service mTLS communication failed"
        return 1
    fi
}

# List Kubernetes certificates
list_k8s_certificates() {
    log_info "Kubernetes Certificates in namespace ${NAMESPACE}:"

    if ! kubectl get certificates -n "$NAMESPACE" 2>/dev/null; then
        log_warning "Failed to list Kubernetes certificates (kubectl not available or namespace not found)"
        return 1
    fi

    log_info "Certificate details:"
    kubectl describe certificates -n "$NAMESPACE" 2>/dev/null | grep -E "Name:|Status:|Renewal|Expiration"
}

# Check certificate rotation
check_certificate_rotation() {
    local service=$1

    log_info "Checking certificate rotation status for ${service}..."

    if ! kubectl describe certificate "${service}-cert" -n "$NAMESPACE" 2>/dev/null; then
        log_warning "Failed to describe certificate ${service}-cert"
        return 1
    fi
}

# Monitor certificate expiration
monitor_certificate_expiration() {
    log_info "Monitoring certificate expiration dates..."

    for service in "${!SERVICES[@]}"; do
        check_certificate_validity "$service"
    done
}

# Print summary report
print_summary() {
    log_info "=== mTLS Testing Summary ==="
    log_info "Namespace: ${NAMESPACE}"
    log_info "Certificate Path: ${CERT_PATH}"
    log_info "Curl Timeout: ${CURL_TIMEOUT}s"
    echo ""
}

# Main execution
main() {
    print_summary

    local failed_tests=0
    local total_tests=0

    # Test certificate files existence
    log_info "Phase 1: Checking certificate files..."
    for service in "${!SERVICES[@]}"; do
        ((total_tests++))
        if ! check_certificate_files "$service"; then
            ((failed_tests++))
        fi
    done
    echo ""

    # Test certificate validity
    log_info "Phase 2: Validating certificates..."
    for service in "${!SERVICES[@]}"; do
        ((total_tests++))
        if ! check_certificate_validity "$service"; then
            ((failed_tests++))
        fi
    done
    echo ""

    # Test certificate chains
    log_info "Phase 3: Testing certificate chains..."
    for service in "${!SERVICES[@]}"; do
        ((total_tests++))
        if ! test_certificate_chain "$service"; then
            ((failed_tests++))
        fi
    done
    echo ""

    # Test service connectivity (only if kubectl available)
    if command -v kubectl &> /dev/null; then
        log_info "Phase 4: Testing service connectivity..."
        for service in "${!SERVICES[@]}"; do
            ((total_tests++))
            if ! test_service_connectivity "$service"; then
                ((failed_tests++))
            fi
        done
        echo ""
    fi

    # Test inter-service communication
    if command -v kubectl &> /dev/null; then
        log_info "Phase 5: Testing inter-service communication..."
        ((total_tests++))
        if ! test_inter_service_communication; then
            ((failed_tests++))
        fi
        echo ""
    fi

    # List Kubernetes certificates
    if command -v kubectl &> /dev/null; then
        log_info "Phase 6: Kubernetes Certificates Status..."
        list_k8s_certificates
        echo ""
    fi

    # Monitor certificate expiration
    log_info "Phase 7: Certificate Expiration Monitoring..."
    monitor_certificate_expiration
    echo ""

    # Print final summary
    log_info "=== Test Execution Summary ==="
    log_info "Total Tests: ${total_tests}"
    log_info "Passed: $((total_tests - failed_tests))"
    log_error "Failed: ${failed_tests}"

    if [[ $failed_tests -eq 0 ]]; then
        log_success "All mTLS tests passed!"
        return 0
    else
        log_error "Some mTLS tests failed!"
        return 1
    fi
}

# Run main function
main "$@"
