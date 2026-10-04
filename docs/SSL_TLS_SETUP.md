# SSL/TLS Setup and Configuration Guide

> **Note (Kubernetes rework):** the nginx manifests this guide references
> moved to `k8s/nginx-https/` (`configmap.yaml`/`deployment.yaml`/
> `service.yaml`, no more numeric prefixes) and are not verified working
> end-to-end as of this pass - see that folder's own README and
> `docs/KUBERNETES_DEPLOYMENT.md`'s "What this round didn't cover" for
> specifics (namely: the `nginx-tls` Secret it mounts has to be created by
> hand now that the cert-manager setup that used to produce it has been
> removed - see `docs/MTLS_CONFIGURATION.md`'s own notice for why).

## Overview

This guide covers SSL/TLS termination at the Nginx reverse proxy for the micro-ecommerce platform. All HTTPS traffic terminates at Nginx, with internal service-to-service communication using HTTP.

## Architecture

```
┌─────────────────────────────────────┐
│        Client/Browser               │
│         (HTTPS/TLS 1.2+)            │
└──────────────────┬──────────────────┘
                   │
                   ▼
         ┌─────────────────────┐
         │   Nginx (Port 443)  │
         │  - SSL Termination  │
         │  - Load Balancing   │
         │  - Security Headers │
         └─────────┬───────────┘
                   │
        ┌──────────┼──────────────┐
        │          │              │
        ▼          ▼              ▼
    ┌──────┐  ┌──────┐  ┌──────────────┐
    │ API  │  │ Auth │  │ Order/Payment│
    │Gateway  │Service  │  Services    │
    └──────┘  └──────┘  └──────────────┘
    (HTTP)   (HTTP)      (HTTP)
```

## Certificate Types

### Local Development
- Self-signed certificates
- Valid for 365 days
- Located in `infrastructure/nginx/certs/`

### Staging
- Let's Encrypt certificates
- Auto-renewal 30 days before expiry
- Wildcard support: `*.ecommerce.local`

### Production
- Let's Encrypt certificates
- Auto-renewal via cron/systemd
- Fully qualified domain names only

## Quick Start

### 1. Local Development Setup

```bash
# Generate self-signed certificates
bash infrastructure/scripts/setup-certificates.sh

# Certificates are created in:
# - infrastructure/nginx/certs/cert.pem
# - infrastructure/nginx/certs/privkey.pem
# - infrastructure/nginx/certs/fullchain.pem
# - infrastructure/nginx/certs/dhparam.pem
```

### 2. Docker Development with Nginx

```bash
# Build and start with Nginx (the https profile, on top of the core stack)
docker compose --profile https up -d

# Test HTTPS connection
curl -k https://localhost/health

# View logs
docker compose logs -f nginx
```

### 3. Run SSL Tests

```bash
# Test SSL/TLS configuration
bash infrastructure/tests/test-ssl.sh

# Test against specific host
HOST=example.com PORT=443 bash infrastructure/tests/test-ssl.sh
```

## Certificate Management

### Viewing Certificate Details

```bash
# Check certificate expiry
openssl x509 -in infrastructure/nginx/certs/cert.pem -text -noout | grep -A2 "Validity"

# View certificate chain
openssl s_client -connect localhost:443 -servername localhost < /dev/null

# Verify DH parameters
openssl dhparam -in infrastructure/nginx/certs/dhparam.pem -text -noout
```

### Renewing Certificates

```bash
# For staging/production (Let's Encrypt)
ENVIRONMENT=staging bash infrastructure/scripts/renew-certificates.sh

# Check certificate renewal log
tail -f infrastructure/scripts/cert-renewal.log
```

### Manual Certificate Installation

```bash
# Copy certificates to Nginx directory
cp /path/to/cert.pem infrastructure/nginx/certs/fullchain.pem
cp /path/to/privkey.pem infrastructure/nginx/certs/privkey.pem
cp /path/to/chain.pem infrastructure/nginx/certs/chain.pem

# Reload Nginx to apply changes
docker exec nginx nginx -s reload
```

## Security Configuration

### TLS Versions
- **Enabled**: TLS 1.2, TLS 1.3
- **Disabled**: SSLv3, TLSv1.0, TLSv1.1

### Ciphers
Configured in order of preference:
1. TLS 1.3 ciphers (AEAD only)
2. ECDHE with AES-GCM
3. ECDHE with ChaCha20-Poly1305

### Security Headers

| Header | Value | Purpose |
|--------|-------|---------|
| HSTS | max-age=31536000 | Force HTTPS for 1 year |
| X-Frame-Options | SAMEORIGIN | Prevent clickjacking |
| X-Content-Type-Options | nosniff | Prevent MIME-type sniffing |
| X-XSS-Protection | 1; mode=block | Legacy XSS protection |
| Referrer-Policy | strict-origin-when-cross-origin | Control referrer leaking |
| Permissions-Policy | geolocation=(), microphone=(), camera=() | Restrict browser features |

### Perfect Forward Secrecy (PFS)
- Enabled via ECDHE key exchange
- Session tickets for performance optimization
- DH parameters: 2048-bit minimum

### OCSP Stapling
- Improves TLS handshake performance
- Includes certificate revocation status
- Responder: `ocsp.int-x3.letsencrypt.org`

## Nginx Configuration

### Main Configuration Files

#### `infrastructure/nginx/nginx.conf`
- Main Nginx configuration
- HTTP → HTTPS redirect (301)
- Service upstream definitions
- Security headers configuration

#### `infrastructure/nginx/ssl.conf`
- TLS protocols and ciphers
- Session configuration
- OCSP stapling settings
- DH parameters

#### `infrastructure/nginx/upstream.conf`
- Backend service definitions
- Load balancing strategy: `least_conn`
- Health checks (max_fails, fail_timeout)
- Connection pooling (keepalive)

### Routing Configuration

| Path | Backend | Port |
|------|---------|------|
| /api/* | api-gateway | 8080 |
| /customers/* | customer-service | 8081 |
| /orders/* | order-service | 8083 |
| /inventory/* | inventory-service | 8082 |
| /payments/* | payment-service | 8084 |

## Docker Deployment

### HTTPS via the `https` Compose profile

Run with the Nginx reverse proxy (see [SETUP_AND_DEPLOYMENT.md](SETUP_AND_DEPLOYMENT.md#https-setup)):
```bash
docker compose --profile https up -d
```

Key features:
- Nginx handles SSL/TLS termination
- All services use internal HTTP
- X-Forwarded-* headers for origin information
- Health checks for all services

### Docker Networking

```yaml
networks:
  ecommerce-network:
    driver: bridge
```

All services communicate via internal DNS:
- `http://api-gateway:8080`
- `http://customer-service:8081`
- `http://order-service:8083`
- `http://inventory-service:8082`
- `http://payment-service:8084`

## Kubernetes Deployment

### Prerequisites

1. Deploy the base stack (creates the namespace as part of it):
```bash
kubectl apply -k k8s/overlays/h2
```

2. Create TLS secret:
```bash
kubectl create secret tls nginx-tls \
  --cert=infrastructure/nginx/certs/fullchain.pem \
  --key=infrastructure/nginx/certs/privkey.pem \
  -n ecommerce
```

### Deploy Nginx

```bash
# Deploy ConfigMap with Nginx configuration
kubectl apply -f k8s/nginx-https/configmap.yaml

# Deploy Nginx Deployment and Service
kubectl apply -f k8s/nginx-https/deployment.yaml
kubectl apply -f k8s/nginx-https/service.yaml
```

### Verify Deployment

```bash
# Check Nginx pods
kubectl get pods -n ecommerce -l app=nginx

# Check service
kubectl get svc -n ecommerce nginx

# View logs
kubectl logs -n ecommerce -l app=nginx -f

# Test health
kubectl exec -n ecommerce -it deployment/nginx -- curl https://localhost/health -k
```

### Updating Certificates

```bash
# Update TLS secret
kubectl delete secret nginx-tls -n ecommerce
kubectl create secret tls nginx-tls \
  --cert=infrastructure/nginx/certs/fullchain.pem \
  --key=infrastructure/nginx/certs/privkey.pem \
  -n ecommerce

# Rollout Nginx deployment to pick up new certificates
kubectl rollout restart deployment/nginx -n ecommerce

# Check rollout status
kubectl rollout status deployment/nginx -n ecommerce
```

## Troubleshooting

### Certificate Issues

#### "Certificate Not Trusted" Error
```bash
# For local development, add certificate to trust store
# macOS:
sudo security add-trusted-cert -d -r trustRoot \
  -k /Library/Keychains/System.keychain \
  infrastructure/nginx/certs/cert.pem

# Linux (Ubuntu/Debian):
sudo cp infrastructure/nginx/certs/cert.pem /usr/local/share/ca-certificates/
sudo update-ca-certificates
```

#### "Certificate Has Expired"
```bash
# Check expiry date
openssl x509 -enddate -noout -in infrastructure/nginx/certs/cert.pem

# Regenerate for local development
rm infrastructure/nginx/certs/*
bash infrastructure/scripts/setup-certificates.sh
```

### Connection Issues

#### "Connection Refused" on Port 443
```bash
# Check if Nginx is running
docker ps | grep nginx

# Check Nginx logs
docker compose logs nginx

# Verify certificate files exist
ls -la infrastructure/nginx/certs/
```

#### "502 Bad Gateway"
```bash
# Check upstream services are running
docker ps | grep -E "api-gateway|customer-service|order-service"

# Check Nginx upstream configuration
docker exec nginx cat /etc/nginx/upstream.conf

# Test backend connectivity
docker exec nginx curl http://api-gateway:8080/health
```

### Performance Issues

#### High TLS Handshake Time
```bash
# Verify OCSP stapling is working
openssl s_client -connect localhost:443 -servername localhost \
  -tlsextdebug -no_ticket 2>/dev/null | grep -A 10 "OCSP response:"

# Check DH parameters are sufficient
openssl dhparam -in infrastructure/nginx/certs/dhparam.pem -text -noout
```

#### Certificate Not Reloading After Renewal
```bash
# Manually reload Nginx
docker exec nginx nginx -s reload

# Verify certificate in memory
docker exec nginx openssl s_client -connect localhost:443 -servername localhost \
  </dev/null 2>/dev/null | grep -A2 "Issuer:"
```

## Monitoring

### Certificate Expiry

Add to monitoring/prometheus.yml:
```yaml
- job_name: 'nginx-cert-expiry'
  static_configs:
    - targets: ['localhost:9114']
```

Monitor metrics:
- `ssl_cert_not_after` - Certificate expiry timestamp
- `ssl_cert_valid_from` - Certificate validity start

### SSL/TLS Metrics

Monitor via Prometheus:
- Connection count
- Handshake failures
- Session reuse rate
- Protocol distribution

### Health Checks

Nginx health endpoint: `/health`
```bash
curl -k https://localhost/health
# Response: healthy
```

## Production Deployment Checklist

- [ ] Certificate acquired from Let's Encrypt
- [ ] Certificate path configured in Nginx
- [ ] TLS 1.3 verified: `openssl s_client -connect ... -tls1_3`
- [ ] Security headers verified: `curl -I https://...`
- [ ] HSTS header active for minimum 1 year
- [ ] Forward secrecy enabled: ECDHE/DHE present
- [ ] Certificate renewal automation set up
- [ ] Certificate renewal tested with dry-run
- [ ] Monitoring and alerts for certificate expiry configured
- [ ] Backup certificates stored securely
- [ ] SSL Labs score >= A (target: A+)
- [ ] All backend services configured for X-Forwarded-* headers
- [ ] Load testing completed with TLS enabled
- [ ] Disaster recovery plan for certificate loss documented

## SSL Labs Testing

Test your configuration at: https://www.ssllabs.com/ssltest/

Target grade: **A+**

Common findings:
- Chain issues: Ensure fullchain.pem includes intermediate certificates
- Weak protocols: Verify TLSv1.0/1.1 are disabled
- Weak ciphers: Check cipher configuration
- Certificate issues: Verify certificate validity and SANs

## References

- [Mozilla SSL Configuration Generator](https://ssl-config.mozilla.org/)
- [OWASP TLS Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Protection_Cheat_Sheet.html)
- [Let's Encrypt Documentation](https://letsencrypt.org/docs/)
- [Nginx SSL Documentation](https://nginx.org/en/docs/http/ngx_http_ssl_module.html)
- [RFC 8446 - TLS 1.3](https://tools.ietf.org/html/rfc8446)

## Support

For issues or questions:
1. Check troubleshooting section above
2. Review Nginx error logs: `docker-compose logs nginx`
3. Run SSL tests: `bash infrastructure/tests/test-ssl.sh`
4. Consult referenced documentation
