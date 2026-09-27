#!/bin/bash

# Certificate Setup Script
# Generates self-signed certificates for local development or Let's Encrypt certificates for production

set -e

CERT_DIR="${CERT_DIR:-.infrastructure/nginx/certs}"
DOMAIN="${DOMAIN:-localhost}"
ENVIRONMENT="${ENVIRONMENT:-local}"
DAYS_VALID="${DAYS_VALID:-365}"
KEY_SIZE="${KEY_SIZE:-2048}"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Create certificate directory
mkdir -p "$CERT_DIR"

log_info "Setting up SSL/TLS certificates for environment: $ENVIRONMENT"

if [ "$ENVIRONMENT" = "local" ] || [ "$ENVIRONMENT" = "staging" ]; then
    # Generate self-signed certificate for local/staging
    log_info "Generating self-signed certificate for $DOMAIN..."

    # Generate private key
    openssl genrsa -out "$CERT_DIR/privkey.pem" "$KEY_SIZE"
    log_info "Generated private key: $CERT_DIR/privkey.pem"

    # Create openssl config file for SAN
    openssl_config=$(mktemp)
    cat > "$openssl_config" << 'EOF'
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
default_bits = 2048
prompt = no

[req_distinguished_name]
C = US
ST = State
L = City
O = Organization
CN = localhost

[v3_req]
subjectAltName = DNS:localhost,DNS:*.ecommerce.local,IP:127.0.0.1
EOF

    # Generate certificate signing request with SAN
    openssl req -new \
        -key "$CERT_DIR/privkey.pem" \
        -out "$CERT_DIR/csr.pem" \
        -config "$openssl_config"
    log_info "Generated CSR: $CERT_DIR/csr.pem"

    # Generate self-signed certificate with SAN
    openssl x509 -req \
        -days "$DAYS_VALID" \
        -in "$CERT_DIR/csr.pem" \
        -signkey "$CERT_DIR/privkey.pem" \
        -out "$CERT_DIR/cert.pem" \
        -extensions v3_req \
        -extfile "$openssl_config"

    rm "$openssl_config"
    log_info "Generated self-signed certificate: $CERT_DIR/cert.pem"

    # Create fullchain.pem (for local, same as cert)
    cp "$CERT_DIR/cert.pem" "$CERT_DIR/fullchain.pem"
    log_info "Created fullchain: $CERT_DIR/fullchain.pem"

    # Create chain.pem (for OCSP stapling)
    cp "$CERT_DIR/cert.pem" "$CERT_DIR/chain.pem"
    log_info "Created chain: $CERT_DIR/chain.pem"

    # Generate DH parameters (2048-bit, takes a while)
    if [ ! -f "$CERT_DIR/dhparam.pem" ]; then
        log_warn "Generating 2048-bit DH parameters (this may take a few minutes)..."
        openssl dhparam -out "$CERT_DIR/dhparam.pem" 2048
        log_info "Generated DH parameters: $CERT_DIR/dhparam.pem"
    fi

    # Generate session ticket key
    openssl rand 48 > "$CERT_DIR/session_ticket.key"
    log_info "Generated session ticket key: $CERT_DIR/session_ticket.key"

    # Set appropriate permissions
    chmod 600 "$CERT_DIR/privkey.pem"
    chmod 644 "$CERT_DIR/cert.pem"
    chmod 644 "$CERT_DIR/fullchain.pem"
    chmod 644 "$CERT_DIR/chain.pem"
    chmod 644 "$CERT_DIR/dhparam.pem"
    chmod 600 "$CERT_DIR/session_ticket.key"

    log_info "Certificate setup completed for $ENVIRONMENT environment"
    log_info "Certificate details:"
    openssl x509 -in "$CERT_DIR/cert.pem" -text -noout | grep -E "Subject:|Issuer:|Not Before|Not After" || true

elif [ "$ENVIRONMENT" = "production" ]; then
    log_error "Production certificate setup requires Let's Encrypt configuration"
    log_info "For Let's Encrypt, use the certificate renewal script or manually run:"
    log_info "  certbot certonly --standalone -d yourdomain.com --email admin@yourdomain.com"
    log_info "Then copy certificates to: $CERT_DIR"
    exit 1

else
    log_error "Unknown environment: $ENVIRONMENT"
    log_info "Valid options: local, staging, production"
    exit 1
fi

log_info "SSL/TLS certificates setup completed successfully!"
