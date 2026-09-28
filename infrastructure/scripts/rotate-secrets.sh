#!/bin/bash

# ============================================================
# Secret Rotation Script
# ============================================================
#
# Usage: ./rotate-secrets.sh <secret-type> <service>
#
# Example:
#   ./rotate-secrets.sh jwt
#   ./rotate-secrets.sh database customer-service
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
NAMESPACE="ecommerce-secrets"
LOG_FILE="/var/log/secret-rotation.log"
VAULT_ENABLED=${VAULT_ENABLED:-false}
VAULT_ADDR=${VAULT_ADDR:-"http://vault.vault.svc.cluster.local:8200"}

# Functions
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1" | tee -a "$LOG_FILE"
}

success() {
    echo -e "${GREEN}✓ $1${NC}" | tee -a "$LOG_FILE"
}

error() {
    echo -e "${RED}✗ $1${NC}" | tee -a "$LOG_FILE"
    exit 1
}

warning() {
    echo -e "${YELLOW}⚠ $1${NC}" | tee -a "$LOG_FILE"
}

# Validate prerequisites
validate_prerequisites() {
    log "Validating prerequisites..."

    # Check if kubectl is installed
    if ! command -v kubectl &> /dev/null; then
        error "kubectl is not installed"
    fi

    # Check if namespace exists
    if ! kubectl get namespace "$NAMESPACE" &> /dev/null; then
        error "Namespace $NAMESPACE does not exist"
    fi

    # Check Vault if enabled
    if [ "$VAULT_ENABLED" = "true" ]; then
        if ! command -v vault &> /dev/null; then
            error "Vault CLI is not installed"
        fi

        if ! vault status &> /dev/null; then
            error "Vault is not accessible at $VAULT_ADDR"
        fi
    fi

    success "Prerequisites validated"
}

# Generate new secret
generate_secret() {
    local secret_type=$1

    log "Generating new $secret_type secret..."

    case $secret_type in
        jwt)
            # Generate 64-character random JWT secret
            local new_secret=$(openssl rand -base64 48 | tr -d "=+/" | cut -c1-50)
            echo "$new_secret"
            ;;
        database)
            # Generate 32-character database password
            local new_secret=$(openssl rand -base64 24 | tr -d "=+/")
            echo "$new_secret"
            ;;
        redis)
            # Generate 32-character Redis password
            local new_secret=$(openssl rand -base64 24 | tr -d "=+/")
            echo "$new_secret"
            ;;
        kafka)
            # Generate 32-character Kafka password
            local new_secret=$(openssl rand -base64 24 | tr -d "=+/")
            echo "$new_secret"
            ;;
        *)
            error "Unknown secret type: $secret_type"
            ;;
    esac
}

# Rotate JWT Secret
rotate_jwt_secret() {
    log "Starting JWT secret rotation..."

    local current_secret
    local new_secret

    # Get current secret
    current_secret=$(kubectl get secret jwt-secret -n "$NAMESPACE" -o jsonpath='{.data.secret}' | base64 -d)

    # Generate new secret
    new_secret=$(generate_secret jwt)

    if [ "$VAULT_ENABLED" = "true" ]; then
        # Update in Vault
        log "Updating JWT secret in Vault..."
        vault kv put secret/ecommerce/jwt \
            secret="$new_secret" \
            old_secret="$current_secret"
        success "JWT secret updated in Vault"
    else
        # Update Kubernetes Secret
        log "Updating JWT secret in Kubernetes..."

        # Create temporary secret with both old and new
        kubectl patch secret jwt-secret \
            -n "$NAMESPACE" \
            --type merge \
            -p "{\"data\":{\"secret\":\"$(echo -n "$new_secret" | base64)\"}}"

        success "JWT secret updated in Kubernetes"
    fi

    # Restart services to pick up new secret
    restart_services "jwt"

    # After successful deployment, remove old secret
    log "Removing old JWT secret..."
    if [ "$VAULT_ENABLED" = "true" ]; then
        vault kv patch secret/ecommerce/jwt \
            -delete=old_secret
        success "Old JWT secret removed from Vault"
    fi

    success "JWT secret rotation completed"
}

# Rotate Database Credentials
rotate_database_secret() {
    local service=${1:-all}

    log "Starting database credential rotation for $service..."

    local new_password
    new_password=$(generate_secret database)

    if [ "$VAULT_ENABLED" = "true" ]; then
        log "Updating database credentials in Vault..."
        vault kv put secret/ecommerce/database \
            username="dbuser" \
            password="$new_password"
        success "Database credentials updated in Vault"
    else
        log "Updating database credentials in Kubernetes..."
        kubectl patch secret database-credentials \
            -n "$NAMESPACE" \
            --type merge \
            -p "{\"data\":{\"password\":\"$(echo -n "$new_password" | base64)\"}}"
        success "Database credentials updated in Kubernetes"
    fi

    # Restart services
    if [ "$service" = "all" ]; then
        restart_services "database"
    else
        log "Restarting $service..."
        kubectl rollout restart deployment "$service" -n ecommerce
    fi

    success "Database credential rotation completed"
}

# Rotate Redis Credentials
rotate_redis_secret() {
    log "Starting Redis credential rotation..."

    local new_password
    new_password=$(generate_secret redis)

    if [ "$VAULT_ENABLED" = "true" ]; then
        log "Updating Redis credentials in Vault..."
        vault kv put secret/ecommerce/redis \
            password="$new_password"
        success "Redis credentials updated in Vault"
    else
        log "Updating Redis credentials in Kubernetes..."
        kubectl patch secret redis-credentials \
            -n "$NAMESPACE" \
            --type merge \
            -p "{\"data\":{\"password\":\"$(echo -n "$new_password" | base64)\"}}"
        success "Redis credentials updated in Kubernetes"
    fi

    # Restart services using Redis
    restart_services "redis"

    success "Redis credential rotation completed"
}

# Rotate Kafka Credentials
rotate_kafka_secret() {
    log "Starting Kafka credential rotation..."

    local new_password
    new_password=$(generate_secret kafka)

    if [ "$VAULT_ENABLED" = "true" ]; then
        log "Updating Kafka credentials in Vault..."
        vault kv put secret/ecommerce/kafka \
            username="kafka-user" \
            password="$new_password"
        success "Kafka credentials updated in Vault"
    else
        log "Updating Kafka credentials in Kubernetes..."
        kubectl patch secret kafka-credentials \
            -n "$NAMESPACE" \
            --type merge \
            -p "{\"data\":{\"password\":\"$(echo -n "$new_password" | base64)\"}}"
        success "Kafka credentials updated in Kubernetes"
    fi

    # Restart services using Kafka
    restart_services "kafka"

    success "Kafka credential rotation completed"
}

# Restart services affected by secret change
restart_services() {
    local secret_type=$1

    log "Restarting services affected by $secret_type secret rotation..."

    local services=()

    case $secret_type in
        jwt)
            services=("customer-service" "order-service" "payment-service" "inventory-service" "api-gateway")
            ;;
        database)
            services=("customer-service" "order-service" "payment-service" "inventory-service")
            ;;
        redis)
            services=("customer-service" "api-gateway")
            ;;
        kafka)
            services=("order-service" "payment-service" "inventory-service")
            ;;
    esac

    for service in "${services[@]}"; do
        log "Restarting $service..."
        if kubectl rollout restart deployment "$service" -n ecommerce &> /dev/null; then
            log "Waiting for $service to be ready..."
            kubectl rollout status deployment "$service" -n ecommerce --timeout=5m
            success "$service restarted and ready"
        else
            warning "Failed to restart $service (deployment may not exist)"
        fi
    done
}

# Audit rotation
audit_rotation() {
    local secret_type=$1

    log "Recording rotation in audit log..."

    local audit_entry="{
        \"timestamp\": \"$(date -u +'%Y-%m-%dT%H:%M:%SZ')\",
        \"action\": \"secret_rotation\",
        \"secret_type\": \"$secret_type\",
        \"namespace\": \"$NAMESPACE\",
        \"user\": \"${USER:-unknown}\",
        \"status\": \"success\"
    }"

    echo "$audit_entry" >> "${LOG_FILE}.audit.json"
    success "Rotation recorded in audit log"
}

# Main
main() {
    local secret_type=${1:-all}
    local service=${2:-}

    log "=================================================="
    log "Secret Rotation Tool"
    log "=================================================="
    log "Secret Type: $secret_type"
    log "Namespace: $NAMESPACE"
    log "Vault Enabled: $VAULT_ENABLED"

    validate_prerequisites

    case $secret_type in
        jwt)
            rotate_jwt_secret
            audit_rotation jwt
            ;;
        database)
            rotate_database_secret "$service"
            audit_rotation database
            ;;
        redis)
            rotate_redis_secret
            audit_rotation redis
            ;;
        kafka)
            rotate_kafka_secret
            audit_rotation kafka
            ;;
        all)
            rotate_jwt_secret
            rotate_database_secret
            rotate_redis_secret
            rotate_kafka_secret
            audit_rotation all
            ;;
        *)
            error "Unknown secret type: $secret_type. Use: jwt, database, redis, kafka, or all"
            ;;
    esac

    log "=================================================="
    success "Secret rotation completed successfully!"
    log "=================================================="
}

# Run main if script is executed (not sourced)
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
