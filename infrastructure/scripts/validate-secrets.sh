#!/bin/bash

# ============================================================
# Secret Validation Script
# ============================================================
#
# Validates that all required secrets are properly configured
# and securely managed across the infrastructure.
#
# Usage: ./validate-secrets.sh [--strict] [--namespace <ns>]
#
# ============================================================

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
NAMESPACE=${NAMESPACE:-ecommerce-secrets}
STRICT_MODE=false
FAILED_CHECKS=0
PASSED_CHECKS=0

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --strict)
            STRICT_MODE=true
            shift
            ;;
        --namespace)
            NAMESPACE="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Functions
log() {
    echo -e "${BLUE}[CHECK]${NC} $1"
}

pass() {
    echo -e "${GREEN}✓ PASS${NC}: $1"
    ((PASSED_CHECKS++))
}

fail() {
    echo -e "${RED}✗ FAIL${NC}: $1"
    ((FAILED_CHECKS++))
    if [ "$STRICT_MODE" = "true" ]; then
        exit 1
    fi
}

warn() {
    echo -e "${YELLOW}⚠ WARN${NC}: $1"
}

# Check if a secret exists
check_secret_exists() {
    local secret_name=$1

    log "Checking if secret '$secret_name' exists..."

    if kubectl get secret "$secret_name" -n "$NAMESPACE" &> /dev/null; then
        pass "Secret '$secret_name' exists"
    else
        fail "Secret '$secret_name' does not exist"
    fi
}

# Check secret content is not empty
check_secret_not_empty() {
    local secret_name=$1
    local key=$2

    log "Checking if secret '$secret_name:$key' is not empty..."

    local value=$(kubectl get secret "$secret_name" -n "$NAMESPACE" -o jsonpath="{.data.$key}" 2>/dev/null || echo "")

    if [ -z "$value" ]; then
        fail "Secret '$secret_name:$key' is empty"
    else
        pass "Secret '$secret_name:$key' contains data"
    fi
}

# Check minimum secret length
check_secret_length() {
    local secret_name=$1
    local key=$2
    local min_length=$3

    log "Checking secret '$secret_name:$key' length >= $min_length..."

    local value=$(kubectl get secret "$secret_name" -n "$NAMESPACE" -o jsonpath="{.data.$key}" 2>/dev/null | base64 -d || echo "")
    local length=${#value}

    if [ "$length" -ge "$min_length" ]; then
        pass "Secret length: $length chars (required: $min_length)"
    else
        fail "Secret length: $length chars (required: $min_length)"
    fi
}

# Check secret is valid base64
check_secret_base64() {
    local secret_name=$1
    local key=$2

    log "Checking if secret '$secret_name:$key' is valid base64..."

    if kubectl get secret "$secret_name" -n "$NAMESPACE" -o jsonpath="{.data.$key}" 2>/dev/null | base64 -d &>/dev/null; then
        pass "Secret is valid base64"
    else
        fail "Secret is not valid base64"
    fi
}

# Check RBAC permissions
check_rbac() {
    log "Checking RBAC permissions..."

    local role_exists=$(kubectl get role ecommerce-secrets-reader -n "$NAMESPACE" 2>/dev/null | wc -l)
    local binding_exists=$(kubectl get rolebinding ecommerce-secrets-read -n "$NAMESPACE" 2>/dev/null | wc -l)

    if [ "$role_exists" -gt 0 ] && [ "$binding_exists" -gt 0 ]; then
        pass "RBAC role and binding configured"
    else
        fail "RBAC role or binding missing"
    fi
}

# Check secrets are not in logs
check_secrets_not_in_logs() {
    log "Checking if secrets are exposed in pod logs..."

    local pods=$(kubectl get pods -n ecommerce -o name 2>/dev/null || echo "")

    if [ -z "$pods" ]; then
        warn "No pods running in ecommerce namespace (skipping log check)"
        return
    fi

    local secrets_found=0

    while IFS= read -r pod; do
        local pod_name=$(echo "$pod" | cut -d/ -f2)

        # Get logs and check for sensitive patterns
        if kubectl logs "$pod" -n ecommerce 2>/dev/null | grep -iE "secret|password|jwt|credential" > /dev/null; then
            warn "Potential secret exposure in logs of $pod_name"
            ((secrets_found++))
        fi
    done <<< "$pods"

    if [ "$secrets_found" -eq 0 ]; then
        pass "No obvious secret patterns found in pod logs"
    else
        fail "Found $secrets_found pod(s) with potential secret exposure in logs"
    fi
}

# Check pod can access secrets
check_pod_secret_access() {
    log "Checking if pods can access secrets..."

    local deployments=$(kubectl get deployments -n ecommerce -o name 2>/dev/null || echo "")

    if [ -z "$deployments" ]; then
        warn "No deployments running in ecommerce namespace (skipping pod access check)"
        return
    fi

    local access_ok=0
    local access_failed=0

    while IFS= read -r deployment; do
        local deploy_name=$(echo "$deployment" | cut -d/ -f2)

        # Check if deployment has environment variables from secrets
        local has_secrets=$(kubectl get deployment "$deploy_name" -n ecommerce -o yaml 2>/dev/null | grep -i "secretKeyRef" | wc -l)

        if [ "$has_secrets" -gt 0 ]; then
            ((access_ok++))
        fi
    done <<< "$deployments"

    if [ "$access_ok" -gt 0 ]; then
        pass "Found $access_ok deployment(s) with secret references"
    else
        warn "No deployments found with secret references (may not be configured yet)"
    fi
}

# Check for hardcoded secrets in configs
check_no_hardcoded_secrets() {
    log "Checking for hardcoded secrets in ConfigMaps..."

    local configmaps=$(kubectl get configmaps -n ecommerce -o name 2>/dev/null || echo "")

    if [ -z "$configmaps" ]; then
        warn "No ConfigMaps in ecommerce namespace"
        return
    fi

    local secrets_found=0

    while IFS= read -r cm; do
        local cm_name=$(echo "$cm" | cut -d/ -f2)

        # Check for common secret patterns
        if kubectl get configmap "$cm_name" -n ecommerce -o yaml 2>/dev/null | grep -iE "password|secret|token|key|credential" > /dev/null; then
            warn "ConfigMap '$cm_name' contains security-sensitive configuration keys"
            ((secrets_found++))
        fi
    done <<< "$configmaps"

    if [ "$secrets_found" -eq 0 ]; then
        pass "No obvious secrets in ConfigMaps"
    fi
}

# Check secret rotation policy
check_secret_rotation_policy() {
    log "Checking secret rotation policy..."

    local rotation_policy=$(kubectl get secret -n "$NAMESPACE" -o jsonpath='{.items[*].metadata.labels.rotation-policy}' 2>/dev/null | tr ' ' '\n' | sort | uniq)

    if [ -z "$rotation_policy" ]; then
        fail "No rotation policy labels found on secrets"
    else
        pass "Rotation policies configured: $(echo "$rotation_policy" | tr '\n' ', ')"
    fi
}

# Summary
print_summary() {
    echo ""
    echo "=================================================="
    echo "Secret Validation Summary"
    echo "=================================================="
    echo -e "Passed: ${GREEN}$PASSED_CHECKS${NC}"
    echo -e "Failed: ${RED}$FAILED_CHECKS${NC}"
    echo "=================================================="

    if [ "$FAILED_CHECKS" -gt 0 ]; then
        exit 1
    fi
}

# Main validation flow
main() {
    echo "=================================================="
    echo "Secret Configuration Validator"
    echo "=================================================="
    echo "Namespace: $NAMESPACE"
    echo "Strict Mode: $STRICT_MODE"
    echo "Time: $(date)"
    echo ""

    # JWT Secret Checks
    echo -e "\n${BLUE}=== JWT Secret Validation ===${NC}"
    check_secret_exists "jwt-secret"
    check_secret_base64 "jwt-secret" "secret"
    check_secret_not_empty "jwt-secret" "secret"
    check_secret_length "jwt-secret" "secret" 32

    # Database Credentials Checks
    echo -e "\n${BLUE}=== Database Credentials Validation ===${NC}"
    check_secret_exists "database-credentials"
    check_secret_base64 "database-credentials" "username"
    check_secret_base64 "database-credentials" "password"
    check_secret_not_empty "database-credentials" "username"
    check_secret_not_empty "database-credentials" "password"

    # Redis Credentials Checks
    echo -e "\n${BLUE}=== Redis Credentials Validation ===${NC}"
    check_secret_exists "redis-credentials"
    check_secret_base64 "redis-credentials" "password"
    check_secret_not_empty "redis-credentials" "password"

    # Kafka Credentials Checks
    echo -e "\n${BLUE}=== Kafka Credentials Validation ===${NC}"
    check_secret_exists "kafka-credentials"
    check_secret_base64 "kafka-credentials" "username"
    check_secret_base64 "kafka-credentials" "password"
    check_secret_not_empty "kafka-credentials" "username"
    check_secret_not_empty "kafka-credentials" "password"

    # Security Checks
    echo -e "\n${BLUE}=== Security Validation ===${NC}"
    check_rbac
    check_no_hardcoded_secrets
    check_pod_secret_access
    check_secrets_not_in_logs
    check_secret_rotation_policy

    # Print summary
    print_summary
}

# Run main
main
