# mTLS (Mutual TLS) Configuration Guide

> **Removed during the Kubernetes rework - this guide describes manifests
> that no longer exist in `k8s/`.** The audit behind that rework found this
> implementation broken on multiple independent axes, not just one typo to
> patch:
>
> - Every `Certificate` in the old `k8s/13-service-certificates.yaml` was
>   issued for `*.default.svc.cluster.local` - the wrong namespace (this
>   project deploys to `ecommerce`, never `default`). TLS hostname
>   verification would have failed for all of them.
> - `SERVER_SSL_KEY_STORE` pointed at a `.crt` file (a PEM certificate) while
>   `SERVER_SSL_KEY_STORE_TYPE` was `PKCS12` - a `.crt` isn't a PKCS12
>   keystore, and nothing anywhere converted cert-manager's `tls.crt`/
>   `tls.key` output into one. Spring Boot would have failed SSL
>   initialization on startup even with the namespace fixed.
> - The old `k8s/15-deployments-mtls-updates.yaml` wasn't really a manifest
>   to apply - its own header comment said it documented updates meant to be
>   hand-merged into the plain-HTTP deployment files - but it shipped full
>   `Deployment` objects sharing those same names, so `kubectl apply -f
>   k8s/` applied both and silently let this one win by filename sort,
>   switching production-looking HTTPS config live without anyone asking
>   for it.
>
> A correct version of this (real keystore generation via an init
> container, at minimum) is a substantial project on its own and was cut
> rather than rebuilt under that rework's local-learning deployment scope -
> see `docs/KUBERNETES_DEPLOYMENT.md`'s "What this round didn't cover" for
> the full writeup. The rest of this document is kept for historical
> reference only; none of its `kubectl apply` commands will work as written.

## Overview

This guide explains the implementation of mutual TLS (mTLS) for service-to-service communication in the micro-ecommerce platform. mTLS ensures that both client and server verify each other's identity using X.509 certificates, providing:

- **Secure Communication**: Encrypted communication between services
- **Authentication**: Services verify each other's identity
- **Non-Repudiation**: Services cannot deny having made or received requests
- **Certificate Rotation**: Automatic certificate renewal without service restart

## Architecture

### Certificate Management

```
┌─────────────────────────────────────────────────┐
│         cert-manager (Kubernetes)               │
│  - Manages certificate lifecycle                │
│  - Automatic renewal at 30 days before expiry   │
│  - 90-day certificate validity period           │
└─────────────────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
    ┌───▼────┐  ┌──────▼──────┐  ┌─────▼─────┐
    │ CA     │  │ Self-signed │  │ Let's Enc  │
    │ Issuer │  │   Issuer    │  │  Issuer    │
    └────────┘  └─────────────┘  └────────────┘
                        │
    ┌───────────────────┼───────────────────┐
    │                   │                   │
┌───▼────┐      ┌──────▼──────┐    ┌──────▼──────┐
│ Service │      │   Service   │    │   Service   │
│Certs    │◄─────┤ Certificates├────│  Secrets    │
└─────────┘      └─────────────┘    └─────────────┘
    │                   │                   │
    └───────────────────┼───────────────────┘
                        │
            ┌───────────▼────────────┐
            │  Service Deployments   │
            │  (Mount Certificates)  │
            └────────────────────────┘
```

## Installation

### 1. Install cert-manager

```bash
# Add cert-manager Helm repository
helm repo add jetstack https://charts.jetstack.io
helm repo update

# Install cert-manager
kubectl create namespace cert-manager
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --version v1.13.0 \
  --set installCRDs=true

# Verify installation
kubectl get pods -n cert-manager
```

### 2. Deploy cert-manager Configuration

```bash
# Apply cert-manager namespace and RBAC
kubectl apply -f k8s/11-cert-manager-namespace.yaml

# Apply certificate issuers (self-signed for development)
kubectl apply -f k8s/12-cert-manager-issuers.yaml

# Verify CA certificate was created
kubectl get certificate -n cert-manager
kubectl get secret ca-key-pair -n cert-manager -o yaml
```

### 3. Generate Service Certificates

```bash
# Apply service certificate resources
kubectl apply -f k8s/13-service-certificates.yaml

# Verify certificates were created
kubectl get certificates -n ecommerce
kubectl get secrets -n ecommerce | grep tls

# Check certificate status
kubectl describe certificate order-service-cert -n ecommerce
```

Expected output:
```
Status:
  Conditions:
    Last Transition Time:  2024-09-27T10:30:00Z
    Message:               Certificate is up to date and has not expired
    Observed Generation:   1
    Reason:                Ready
    Status:                True
    Type:                  Ready
```

## Service Configuration

### 1. Update Application Properties

Each service needs to be configured to use TLS. Update the application configuration:

**Order Service Example:**
```yaml
server:
  port: 8083
  ssl:
    enabled: true
    key-store-type: PKCS12
    key-store: file:/etc/certs/order-service/tls.crt
    key-store-password: changeit
    client-auth: need  # Require client certificates (mTLS)
    key-alias: order-service
```

### 2. Spring Boot SSL Configuration

Create `RestClientConfig` bean for inter-service communication:

```java
@Configuration
@RequiredArgsConstructor
public class RestClientConfig {
    
    private final SslBundles sslBundles;
    
    @Bean(name = "inventoryServiceRestTemplate")
    public RestTemplate inventoryServiceRestTemplate(RestTemplateBuilder builder) {
        return builder
            .requestFactory(() -> createSslHttpRequestFactory("inventory-service"))
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    private HttpComponentsClientHttpRequestFactory createSslHttpRequestFactory(String serviceName) {
        var factory = new HttpComponentsClientHttpRequestFactory();
        var sslBundle = sslBundles.getBundle("mTLS");
        SSLContext sslContext = sslBundle.createSslContext();
        
        var httpClient = HttpClients.custom()
            .setSSLContext(sslContext)
            .build();
        
        factory.setHttpClient(httpClient);
        return factory;
    }
}
```

### 3. Update Kubernetes Deployments

Apply the updated deployment specifications that include certificate volumes:

```bash
# Review the deployment updates
cat k8s/15-deployments-mtls-updates.yaml

# Apply updates to existing deployments
# Note: This will require updating the deployment YAML files
# OR creating new deployments with the mTLS configuration
```

**Key deployment changes:**
- Mount certificate secrets as volumes
- Set SSL environment variables
- Update health check schemes to HTTPS
- Update service discovery URLs to use HTTPS

### 4. Mount Certificates in Pods

Each pod needs certificate volumes mounted:

```yaml
spec:
  containers:
  - name: order-service
    volumeMounts:
    - name: order-service-tls
      mountPath: /etc/certs/order-service
      readOnly: true
    - name: ca-certificate
      mountPath: /etc/certs/ca
      readOnly: true
  volumes:
  - name: order-service-tls
    secret:
      secretName: order-service-tls
      defaultMode: 0400
  - name: ca-certificate
    secret:
      secretName: ca-key-pair
      defaultMode: 0400
```

## Service-to-Service Communication

### Update Inter-Service URLs

Change service endpoints from HTTP to HTTPS:

**Before:**
```properties
service.inventory.url=http://inventory-service:8082
service.payment.url=http://payment-service:8084
```

**After:**
```properties
service.inventory.url=https://inventory-service.ecommerce.svc.cluster.local:8082
service.payment.url=https://payment-service.ecommerce.svc.cluster.local:8084
```

### Inter-Service Call Example

**Order Service calling Inventory Service:**

```java
@Service
@RequiredArgsConstructor
public class InventoryServiceClient {
    
    @Qualifier("inventoryServiceRestTemplate")
    private final RestTemplate restTemplate;
    
    public InventoryResponse checkInventory(Long productId, int quantity) {
        String url = "https://inventory-service.ecommerce.svc.cluster.local:8082" + 
                    "/api/inventory/check?productId=" + productId + "&quantity=" + quantity;
        
        return restTemplate.getForObject(url, InventoryResponse.class);
    }
}
```

## Certificate Lifecycle Management

### Automatic Certificate Renewal

Certificates are automatically renewed 30 days before expiry:

```bash
# Monitor certificate renewal
kubectl describe certificate order-service-cert -n ecommerce

# Watch certificate status
kubectl get certificate -n ecommerce -w
```

### Manual Certificate Renewal (if needed)

```bash
# Delete the certificate secret to force renewal
kubectl delete secret order-service-tls -n ecommerce

# The Certificate resource will create a new secret automatically
kubectl get secret order-service-tls -n ecommerce -o yaml
```

### Certificate Expiration Alerts

Monitor certificate expiration:

```bash
# Check all certificate expiration dates
for cert in $(kubectl get certificate -n ecommerce -o jsonpath='{.items[*].metadata.name}'); do
  echo "Certificate: $cert"
  kubectl get secret $(kubectl get certificate $cert -n ecommerce -o jsonpath='{.spec.secretName}') \
    -n ecommerce -o jsonpath='{.data.tls\.crt}' | base64 -d | openssl x509 -noout -enddate
done
```

## Testing and Validation

### Run mTLS Tests

```bash
# Run the mTLS testing script
./infrastructure/tests/test-mtls.sh

# Expected output:
# [SUCCESS] Certificate for order-service is valid
# [SUCCESS] mTLS connectivity to order-service successful
# [SUCCESS] Certificate chain validation successful for order-service
# [SUCCESS] Inter-service mTLS communication successful
# [SUCCESS] All mTLS tests passed!
```

### Manual Certificate Verification

```bash
# Extract and verify certificate
kubectl get secret order-service-tls -n ecommerce \
  -o jsonpath='{.data.tls\.crt}' | base64 -d | openssl x509 -text -noout

# Verify certificate chain
kubectl get secret order-service-tls -n ecommerce \
  -o jsonpath='{.data.tls\.crt}' | base64 -d > /tmp/order-service.crt

kubectl get secret ca-key-pair -n cert-manager \
  -o jsonpath='{.data.tls\.crt}' | base64 -d > /tmp/ca.crt

openssl verify -CAfile /tmp/ca.crt /tmp/order-service.crt
```

### Test Service-to-Service Communication

```bash
# From Order Service pod, test call to Inventory Service
kubectl exec -it deployment/order-service -n ecommerce -- bash

# Inside the pod
curl --cacert /etc/certs/ca/ca.crt \
     --cert /etc/certs/order-service/tls.crt \
     --key /etc/certs/order-service/tls.key \
     https://inventory-service.ecommerce.svc.cluster.local:8082/actuator/health

# Expected response:
# {"status":"UP","components":{"diskSpace":{...}}}
```

### Test Certificate Validation Failures

```bash
# Test without client certificate (should fail)
curl -k https://order-service.ecommerce.svc.cluster.local:8083/actuator/health
# Expected: Connection refused or certificate required error

# Test with invalid certificate (should fail)
curl --cacert /etc/certs/ca/ca.crt \
     https://order-service.ecommerce.svc.cluster.local:8083/actuator/health
# Expected: Peer certificate cannot be authenticated
```

## Troubleshooting

### Certificate Not Found

**Error:** `java.io.FileNotFoundException: /etc/certs/order-service/tls.crt`

**Solution:**
1. Verify secret exists: `kubectl get secret order-service-tls -n ecommerce`
2. Check volume mount: `kubectl describe pod -n ecommerce | grep -A 5 order-service-tls`
3. Restart pod: `kubectl rollout restart deployment/order-service -n ecommerce`

### Certificate Verification Failed

**Error:** `sun.security.validator.ValidatorException: PKIX path building failed`

**Solution:**
1. Verify certificate chain: `kubectl describe certificate order-service-cert -n ecommerce`
2. Check CA certificate: `kubectl get secret ca-key-pair -n cert-manager`
3. Ensure CA certificate is mounted in pod

### mTLS Handshake Failure

**Error:** `javax.net.ssl.SSLHandshakeException: Received fatal alert: handshake_failure`

**Solution:**
1. Verify client certificate is valid: `openssl x509 -in /etc/certs/order-service/tls.crt -text`
2. Check server requires mTLS: `server.ssl.client-auth=need`
3. Verify certificate chain is complete

### Certificate Renewal Issues

**Problem:** Certificates not renewing automatically

**Solution:**
1. Check cert-manager logs: `kubectl logs -n cert-manager -l app=cert-manager`
2. Verify issuer status: `kubectl describe clusterissuer ca-issuer -n cert-manager`
3. Check certificate conditions: `kubectl describe certificate order-service-cert -n ecommerce`

### Service Discovery with HTTPS

**Error:** `UnknownHostException` when using service DNS names

**Solution:**
1. Verify DNS resolution in pod
2. Use full qualified domain name: `order-service.ecommerce.svc.cluster.local`
3. Ensure network policies allow inter-service communication

## Real-World Scenario: Preventing Service Spoofing

### Scenario
An attacker attempts to intercept and spoof the Order Service to steal payment information.

### Without mTLS
1. Attacker creates a fake Order Service pod
2. Routes traffic to fake service using DNS spoofing
3. Payment Service doesn't verify Order Service identity
4. Payment information is captured

### With mTLS
1. Attacker creates a fake Order Service pod
2. Fake service has no valid certificate signed by CA
3. Payment Service verifies certificate during TLS handshake
4. Connection is rejected - attacker cannot establish mTLS session
5. All requests fail at the TLS layer before application code

### Audit Trail Example

```log
2024-09-27 10:15:32 ORDER-SERVICE - Certificate validation successful for subject: CN=order-service.ecommerce.svc.cluster.local
2024-09-27 10:15:32 PAYMENT-SERVICE - TLS handshake successful with order-service (cert serial: 0x1234567890)
2024-09-27 10:15:33 PAYMENT-SERVICE - Processed payment request from order-service
2024-09-27 10:16:45 PAYMENT-SERVICE - TLS handshake failed with unknown client (cert not trusted)
2024-09-27 10:16:45 PAYMENT-SERVICE - REJECTED: Unable to verify client certificate
```

## Monitoring and Alerts

### Prometheus Metrics

Add monitoring for certificate expiration:

```yaml
- alert: CertificateExpiringWarning
  expr: certmanager_certificate_expiration_timestamp_seconds - time() < 30 * 24 * 3600
  for: 1h
  labels:
    severity: warning
  annotations:
    summary: "Certificate {{ $labels.certificate }} expiring in 30 days"

- alert: CertificateExpiredCritical
  expr: certmanager_certificate_expiration_timestamp_seconds - time() < 0
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "Certificate {{ $labels.certificate }} has expired"
```

### Health Checks

Services expose certificate status in health checks:

```bash
curl https://order-service.ecommerce.svc.cluster.local:8083/actuator/health
```

Response:
```json
{
  "status": "UP",
  "components": {
    "certificateHealth": {
      "status": "UP",
      "details": {
        "certificate": "order-service",
        "expiresIn": "89 days",
        "issuer": "micro-ecommerce-ca"
      }
    }
  }
}
```

## Performance Considerations

### TLS Overhead

- **Connection Setup**: ~5-10ms for TLS handshake
- **Data Transfer**: <1% overhead for AES-256-GCM encryption
- **Mitigation**: Use HTTP/2 with connection pooling

### Connection Pooling Configuration

```java
@Configuration
public class HttpClientConfig {
    
    @Bean
    public PoolingHttpClientConnectionManager connectionManager() {
        PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
        manager.setMaxTotal(200);
        manager.setDefaultMaxPerRoute(50);
        return manager;
    }
}
```

## Migration Strategy

### Phase 1: Preparation
1. Deploy cert-manager and generate certificates
2. Configure SSL in Spring Boot (server.ssl.enabled=true but client-auth=want)
3. Create SSL RestTemplate beans
4. Test HTTPS connectivity

### Phase 2: Soft Enforcement
1. Set client-auth=want (optional client certificates)
2. Deploy updated services with mTLS RestTemplates
3. Monitor logs for certificate validation errors
4. Update inter-service URLs to HTTPS

### Phase 3: Strict Enforcement
1. Set client-auth=need (require client certificates)
2. Monitor mTLS handshake errors
3. Update all remaining services
4. Decommission HTTP endpoints

## Security Best Practices

1. **Certificate Validation**: Always validate certificate chains
2. **Secure Storage**: Store private keys with restricted permissions (0400)
3. **Certificate Rotation**: Implement automated renewal
4. **Audit Logging**: Log all certificate validations and failures
5. **Monitoring**: Alert on certificate expiration
6. **Network Policy**: Restrict inter-service communication at network level
7. **Certificate Pinning**: Consider certificate pinning for critical paths

## References

- [cert-manager Documentation](https://cert-manager.io/docs/)
- [Spring Boot TLS Configuration](https://spring.io/blog/2023/06/07/securing-spring-boot-applications-with-ssl-tls)
- [Kubernetes mTLS Best Practices](https://kubernetes.io/docs/concepts/security/mutual-tls-migration/)
- [OWASP Transport Layer Protection](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Protection_Cheat_Sheet.html)
