# Phase 2 mTLS Quick Start Guide

## 5-Minute Setup

### Prerequisites
- Kubernetes cluster running
- kubectl configured
- Helm 3.x installed

### Installation Steps

#### 1. Install cert-manager (5 min)
```bash
# Add Helm repository
helm repo add jetstack https://charts.jetstack.io
helm repo update

# Install cert-manager
kubectl create namespace cert-manager
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --set installCRDs=true \
  --wait

# Verify
kubectl get pods -n cert-manager
```

#### 2. Setup Certificates (2 min)
```bash
cd /path/to/micro-eCommerce

# Apply cert-manager configuration
kubectl apply -f k8s/11-cert-manager-namespace.yaml
kubectl apply -f k8s/12-cert-manager-issuers.yaml

# Verify
kubectl wait --for=condition=ready certificate --all -n ecommerce --timeout=300s
```

#### 3. Generate Service Certificates (1 min)
```bash
# Create certificates for all services
kubectl apply -f k8s/13-service-certificates.yaml

# Check status
kubectl get certificates -n ecommerce
kubectl get secrets -n ecommerce | grep tls
```

#### 4. Deploy mTLS Configuration (1 min)
```bash
# Apply ConfigMaps and environment setup
kubectl apply -f k8s/14-service-mtls-configmap.yaml
```

### Verification

```bash
# Check all certificates are Ready
kubectl get certificates -n ecommerce

# Expected output:
# NAME                      READY   SECRET                    AGE
# customer-service-cert     True    customer-service-tls      2m
# order-service-cert        True    order-service-tls         2m
# inventory-service-cert    True    inventory-service-tls     2m
# payment-service-cert      True    payment-service-tls       2m
```

## Next: Application Changes

### Enable mTLS Profile
Each service needs to activate the mTLS configuration:

```bash
# Add to Kubernetes Deployment environment
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "mtls"
```

Or update the deployment volume mount:
```yaml
volumeMounts:
- name: service-tls
  mountPath: /etc/certs/{service-name}
  readOnly: true
volumes:
- name: service-tls
  secret:
    secretName: {service-name}-tls
```

### Update Application Deployment
Reference: `k8s/15-deployments-mtls-updates.yaml`

### Test mTLS

```bash
# Run comprehensive tests
./infrastructure/tests/test-mtls.sh

# Expected: All tests pass

# Test specific service
kubectl exec -it deployment/order-service -n ecommerce -- bash
curl --cacert /etc/certs/ca/ca.crt \
     --cert /etc/certs/order-service/tls.crt \
     --key /etc/certs/order-service/tls.key \
     https://inventory-service.ecommerce.svc.cluster.local:8082/actuator/health
```

## Common Commands

### View Certificate Details
```bash
# Get certificate expiration date
kubectl get certificate order-service-cert -n ecommerce \
  -o jsonpath='{.status.renewalTime}'

# View full certificate info
kubectl get secret order-service-tls -n ecommerce \
  -o jsonpath='{.data.tls\.crt}' | base64 -d | \
  openssl x509 -text -noout
```

### Monitor Certificate Renewal
```bash
# Watch certificate status
kubectl get certificate -n ecommerce -w

# Check renewal status
kubectl describe certificate order-service-cert -n ecommerce | \
  grep -E "Status:|Renewal Time|Expiration"
```

### Troubleshoot Issues
```bash
# Check cert-manager logs
kubectl logs -n cert-manager -l app=cert-manager -f

# Check service logs
kubectl logs -f deployment/order-service -n ecommerce

# Verify certificate in pod
kubectl exec -it deployment/order-service -n ecommerce -- \
  openssl x509 -in /etc/certs/order-service/tls.crt -text -noout
```

## Configuration Files Overview

| File | Purpose |
|------|---------|
| `k8s/11-cert-manager-namespace.yaml` | cert-manager setup |
| `k8s/12-cert-manager-issuers.yaml` | Certificate issuers (CA, Let's Encrypt) |
| `k8s/13-service-certificates.yaml` | Service certificate resources |
| `k8s/14-service-mtls-configmap.yaml` | mTLS configuration maps |
| `k8s/15-deployments-mtls-updates.yaml` | Deployment examples |
| `services/*/src/main/java/*/config/RestClientConfig.java` | Spring Boot SSL config |
| `services/*/src/main/resources/application-mtls.yml` | Spring Boot mTLS profile |
| `infrastructure/tests/test-mtls.sh` | mTLS testing script |
| `docs/MTLS_CONFIGURATION.md` | Complete guide |

## Environment Variables for Services

```bash
# SSL/TLS Configuration
SERVER_SSL_ENABLED=true
SERVER_SSL_KEY_STORE_TYPE=PKCS12
SERVER_SSL_KEY_STORE=/etc/certs/{service-name}/tls.crt
SERVER_SSL_KEY_STORE_PASSWORD=changeit
SERVER_SSL_CLIENT_AUTH=need
SERVER_SSL_KEY_ALIAS={service-name}

# Service URLs (HTTPS)
SERVICE_INVENTORY_URL=https://inventory-service.ecommerce.svc.cluster.local:8082
SERVICE_ORDER_URL=https://order-service.ecommerce.svc.cluster.local:8083
SERVICE_PAYMENT_URL=https://payment-service.ecommerce.svc.cluster.local:8084
SERVICE_CUSTOMER_URL=https://customer-service.ecommerce.svc.cluster.local:8081

# Enable mTLS Profile
SPRING_PROFILES_ACTIVE=mtls
```

## Typical Issues & Solutions

### Issue: Certificate Not Found
```bash
# Solution: Verify secret exists
kubectl get secret order-service-tls -n ecommerce

# If missing, check certificate resource
kubectl describe certificate order-service-cert -n ecommerce

# Wait for cert-manager to generate it (may take 1-2 minutes)
```

### Issue: Connection Refused
```bash
# Solution: Verify service is listening on HTTPS
kubectl port-forward svc/order-service 8083:8083 -n ecommerce

# Test connection
curl -k https://localhost:8083/actuator/health

# Check logs for SSL errors
kubectl logs deployment/order-service -n ecommerce | grep -i ssl
```

### Issue: Certificate Validation Failed
```bash
# Solution: Verify certificate chain
kubectl get secret ca-key-pair -n cert-manager -o yaml

# Extract and verify certificate
kubectl get secret order-service-tls -n ecommerce \
  -o jsonpath='{.data.tls\.crt}' | base64 -d | \
  openssl x509 -noout -text | grep -E "Subject:|Issuer:"
```

## Rollback (If Needed)

```bash
# Step 1: Disable mTLS enforcement (make optional)
kubectl set env deployment/{service} SERVER_SSL_CLIENT_AUTH=want -n ecommerce

# Step 2: Revert to HTTP URLs
# Update application-mtls.yml or use default profile

# Step 3: Restart services
kubectl rollout restart deployment --all -n ecommerce

# Step 4: Remove cert-manager (if completely rolling back)
helm uninstall cert-manager -n cert-manager
kubectl delete namespace cert-manager
```

## Success Checklist

- [ ] cert-manager is running in `cert-manager` namespace
- [ ] All certificates show `READY` status
- [ ] Service secrets contain `tls.crt`, `tls.key`, `ca.crt`
- [ ] Services can access `/actuator/health` via HTTPS
- [ ] Inter-service communication works with mTLS
- [ ] Certificate renewal runs automatically
- [ ] mTLS tests pass
- [ ] No SSL/TLS errors in service logs

## Next Steps

1. **Review Full Guide**: Read `docs/MTLS_CONFIGURATION.md` for detailed info
2. **Check Implementation**: See `docs/PHASE2_MTLS_IMPLEMENTATION.md` for complete details
3. **Run Tests**: Execute `./infrastructure/tests/test-mtls.sh`
4. **Monitor**: Watch certificate status with `kubectl get certificate -w -n ecommerce`

## Support Resources

- **Configuration Guide**: `docs/MTLS_CONFIGURATION.md`
- **Implementation Details**: `docs/PHASE2_MTLS_IMPLEMENTATION.md`
- **Spring Boot Config**: `services/*/src/main/resources/application-mtls.yml`
- **Testing**: `infrastructure/tests/test-mtls.sh`
- **Kubernetes Configs**: `k8s/11-15-*.yaml`

---
**Time to Complete**: ~15-30 minutes (depending on cluster size)  
**Difficulty**: Medium  
**Risk**: Low (can be rolled back easily)
