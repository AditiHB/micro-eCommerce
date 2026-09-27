#!/bin/bash

# Certificate Renewal Script
# Handles Let's Encrypt certificate renewal with auto-renewal capability

set -e

CERT_DIR="${CERT_DIR:-./infrastructure/nginx/certs}"
DOMAIN="${DOMAIN:-ecommerce.local}"
EMAIL="${EMAIL:-admin@ecommerce.local}"
ENVIRONMENT="${ENVIRONMENT:-staging}"
LOG_FILE="${LOG_FILE:-./infrastructure/scripts/cert-renewal.log}"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} [INFO] $1" | tee -a "$LOG_FILE"
}

log_warn() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} [WARN] $1" | tee -a "$LOG_FILE"
}

log_error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} [ERROR] $1" | tee -a "$LOG_FILE"
}

log_debug() {
    if [ "$DEBUG" = "true" ]; then
        echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} [DEBUG] $1" | tee -a "$LOG_FILE"
    fi
}

# Create log directory
mkdir -p "$(dirname "$LOG_FILE")"

log_info "Starting certificate renewal process for $ENVIRONMENT environment"

case "$ENVIRONMENT" in
    local)
        log_info "Local environment: self-signed certificates don't need renewal"
        log_warn "To regenerate certificates, run: bash infrastructure/scripts/setup-certificates.sh"
        ;;

    staging)
        log_info "Staging environment: attempting Let's Encrypt renewal"

        if ! command -v certbot &> /dev/null; then
            log_error "certbot not found. Install with: pip install certbot"
            exit 1
        fi

        # Attempt renewal
        log_info "Running certbot renew..."
        if certbot renew --dry-run; then
            log_info "Dry run successful, proceeding with renewal"
            certbot renew \
                --email "$EMAIL" \
                --agree-tos \
                --no-eff-email \
                --quiet

            log_info "Certificate renewal completed"

            # Copy certificates to Nginx directory
            if [ -d "/etc/letsencrypt/live/$DOMAIN" ]; then
                log_info "Copying certificates to $CERT_DIR"
                cp "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" "$CERT_DIR/fullchain.pem"
                cp "/etc/letsencrypt/live/$DOMAIN/privkey.pem" "$CERT_DIR/privkey.pem"
                cp "/etc/letsencrypt/live/$DOMAIN/chain.pem" "$CERT_DIR/chain.pem"
                chmod 600 "$CERT_DIR/privkey.pem"
                chmod 644 "$CERT_DIR/fullchain.pem"
                chmod 644 "$CERT_DIR/chain.pem"
                log_info "Certificates copied successfully"

                # Reload Nginx
                if command -v docker &> /dev/null && docker ps | grep -q nginx; then
                    log_info "Reloading Nginx..."
                    docker exec nginx nginx -s reload
                    log_info "Nginx reloaded successfully"
                fi
            else
                log_error "Certificate directory not found: /etc/letsencrypt/live/$DOMAIN"
                exit 1
            fi
        else
            log_error "Certificate renewal dry run failed"
            exit 1
        fi
        ;;

    production)
        log_info "Production environment: attempting Let's Encrypt renewal"

        if ! command -v certbot &> /dev/null; then
            log_error "certbot not found. Install with: pip install certbot"
            exit 1
        fi

        # Attempt renewal with webroot plugin
        log_info "Running certbot renew for production..."
        if certbot renew \
            --email "$EMAIL" \
            --agree-tos \
            --no-eff-email \
            --quiet \
            --deploy-hook "systemctl reload nginx"; then
            log_info "Production certificate renewal completed"
        else
            log_error "Certificate renewal failed"
            exit 1
        fi
        ;;

    *)
        log_error "Unknown environment: $ENVIRONMENT"
        log_info "Valid options: local, staging, production"
        exit 1
        ;;
esac

log_info "Certificate renewal process completed"
