# Secrets Management Guide

## Overview

This document provides comprehensive guidance on managing sensitive data (JWT keys, database passwords, API credentials, etc.) across development, staging, and production environments using Kubernetes Secrets and HashiCorp Vault.

## Table of Contents

1. [Development Setup](#development-setup)
2. [Staging Setup](#staging-setup)
3. [Production Setup](#production-setup)
4. [Secret Rotation](#secret-rotation)
5. [Troubleshooting](#troubleshooting)

---

## Development Setup

### Kubernetes Secrets (Local Development)

For local development using Docker Desktop or minikube, we use Kubernetes Secrets with base64 encoding.

#### Step 1: Create Secrets Namespace

```bash
kubectl apply -f k8s/15-secrets-namespace.yaml
```

#### Step 2: Create Secrets

```bash
kubectl apply -f k8s/16-service-secrets.yaml
```

#### Secrets Available

- **jwt-secret**: JWT token signing key
- **database-credentials**: Database connection credentials (username/password)
- **redis-credentials**: Redis authentication password
- **kafka-credentials**: Kafka broker credentials
- **elasticsearch-credentials**: Elasticsearch authentication

#### Step 3: Verify Secrets

```bash
# List all secrets
kubectl get secrets -n ecommerce-secrets

# View a secret (base64 decoded)
kubectl get secret jwt-secret -n ecommerce-secrets -o jsonpath='{.data.secret}' | base64 -d
```

### Environment Variables

Secrets are injected into pods as environment variables. Services read them via Spring configuration:

```yaml
# In application.yml
jwt:
  secret: ${JWT_SECRET:defaultSecretForLocalDev}
  expiration: 86400000

spring:
  datasource:
    username: ${DB_USERNAME:sa}
    password: ${DB_PASSWORD:password}
    url: ${DB_URL:jdbc:h2:mem:testdb}
  redis:
    password: ${REDIS_PASSWORD:}
```

### Local Docker Compose (Without Kubernetes)

If running without Kubernetes, use a `.env` file - Compose loads a file literally named `.env` in the project root automatically, so no `--env-file` flag is needed for it:

```bash
# .env file (repo root)
JWT_SECRET=your-jwt-secret-key-here
```

```bash
docker compose up -d
```

`JWT_SECRET` defaults to a clearly-labeled local-dev-only value in `docker-compose.yml` if you don't set one, so the stack still boots without any setup - just don't rely on that default past local development. For Postgres credentials, see `.env.postgres` and the `postgres` Compose profile in [SETUP_AND_DEPLOYMENT.md](SETUP_AND_DEPLOYMENT.md#local-setup-scenarios) (the username/password there are fixed per-container local dev credentials, not meant to be rotated the way `JWT_SECRET` is).

---

## Staging Setup

### Kubernetes Secrets with Encryption at Rest

For staging, enable encryption at rest in Kubernetes etcd:

#### Enable etcd Encryption

```bash
# Edit kube-apiserver
kubectl edit pod kube-apiserver -n kube-system

# Add encryption provider:
--encryption-provider-config=/etc/kubernetes/encryption-config.yaml
--encryption-provider-config-automatic-reload=true
```

#### Encryption Config File

```yaml
apiVersion: apiserver.config.k8s.io/v1
kind: EncryptionConfiguration
resources:
  - resources:
      - secrets
    providers:
      - aescbc:
          keys:
            - name: key1
              secret: <base64-encoded-32-byte-key>
      - identity: {}
```

#### Recreate Secrets (to re-encrypt)

```bash
kubectl get secrets --all-namespaces -o json | kubectl replace -f -
```

### RBAC (Role-Based Access Control)

Restrict secret access to only services that need them:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: customer-service-role
  namespace: ecommerce-secrets
rules:
- apiGroups: [""]
  resources: ["secrets"]
  resourceNames: ["jwt-secret", "database-credentials"]
  verbs: ["get"]
```

---

## Production Setup

### HashiCorp Vault Integration

For production, use HashiCorp Vault for superior security and audit logging.

#### Step 1: Install Vault

```bash
# Using Helm
helm repo add hashicorp https://helm.releases.hashicorp.com
helm install vault hashicorp/vault -n vault --create-namespace
```

#### Step 2: Initialize Vault

```bash
# Port forward to Vault
kubectl port-forward -n vault svc/vault 8200:8200

# Initialize (creates unseal keys and root token)
vault operator init -key-shares=5 -key-threshold=3

# Unseal Vault (requires 3 of 5 keys)
vault operator unseal <key1>
vault operator unseal <key2>
vault operator unseal <key3>
```

#### Step 3: Configure Secret Paths

```bash
# Enable KV v2 secrets engine
vault secrets enable -path=secret kv-v2

# Create JWT secret
vault kv put secret/ecommerce/jwt \
  secret="your-jwt-signing-key"

# Create database credentials
vault kv put secret/ecommerce/database \
  username="dbuser" \
  password="dbpassword"

# Create Redis credentials
vault kv put secret/ecommerce/redis \
  password="redispassword"
```

#### Step 4: Configure Kubernetes Authentication

```bash
# Enable Kubernetes auth method
vault auth enable kubernetes

# Configure K8s auth
vault write auth/kubernetes/config \
  token_reviewer_jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token \
  kubernetes_host=https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT \
  kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt
```

#### Step 5: Create Service Roles

```bash
# Create policy for services
vault policy write ecommerce-policy - <<EOF
path "secret/data/ecommerce/*" {
  capabilities = ["read"]
}
EOF

# Create Kubernetes role
vault write auth/kubernetes/role/ecommerce-services \
  bound_service_account_names=ecommerce-services \
  bound_service_account_namespaces=ecommerce \
  policies=ecommerce-policy \
  ttl=24h
```

#### Step 6: Update Spring Boot for Vault

Add to pom.xml:

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-vault-config</artifactId>
  <version>3.1.x</version>
</dependency>
```

Update application.yml:

```yaml
spring:
  cloud:
    vault:
      host: vault.vault.svc.cluster.local
      port: 8200
      scheme: https
      authentication: KUBERNETES
      kubernetes:
        role: ecommerce-services
        service-account-token-file: /var/run/secrets/kubernetes.io/serviceaccount/token
      generic:
        enabled: true
        backend-path: secret
      kv:
        enabled: true
        backend-path: secret
```

---

## Secret Rotation

### Manual Rotation

#### Rotate JWT Secret (with Zero Downtime)

```bash
#!/bin/bash
# 1. Create new secret in Vault
vault kv put secret/ecommerce/jwt \
  secret="new-jwt-signing-key" \
  old_secret="current-jwt-signing-key"

# 2. Deploy new version of services that reads old_secret as fallback
kubectl rollout restart deployment/customer-service -n ecommerce

# 3. After successful deployment, remove old_secret
vault kv patch secret/ecommerce/jwt \
  secret="new-jwt-signing-key"

# 4. Deploy final version without fallback
kubectl rollout restart deployment/customer-service -n ecommerce
```

#### Rotate Database Credentials

```bash
#!/bin/bash
# 1. Create new DB user with same permissions
createuser new_user
ALTER USER new_user WITH PASSWORD 'new_password';

# 2. Update secret in Vault
vault kv put secret/ecommerce/database \
  username="new_user" \
  password="new_password"

# 3. Restart services
kubectl rollout restart deployment -n ecommerce

# 4. Remove old user from database
dropuser old_user;
```

### Automated Rotation

Create a Kubernetes CronJob for automatic rotation:

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: secret-rotation
  namespace: ecommerce
spec:
  schedule: "0 0 * * *"  # Daily at midnight
  jobTemplate:
    spec:
      template:
        spec:
          serviceAccountName: secret-rotator
          containers:
          - name: rotator
            image: hashicorp/vault:latest
            env:
            - name: VAULT_ADDR
              value: "https://vault.vault.svc.cluster.local:8200"
            command:
            - /bin/sh
            - -c
            - |
              /scripts/rotate-secrets.sh
            volumeMounts:
            - name: rotation-script
              mountPath: /scripts
          volumes:
          - name: rotation-script
            configMap:
              name: secret-rotation-script
          restartPolicy: OnFailure
```

---

## Secret Validation

### Pre-Deployment Validation

```bash
#!/bin/bash
# Check all required secrets exist

REQUIRED_SECRETS=(
  "jwt-secret"
  "database-credentials"
  "redis-credentials"
)

for secret in "${REQUIRED_SECRETS[@]}"; do
  if kubectl get secret "$secret" -n ecommerce-secrets &> /dev/null; then
    echo "✓ Secret '$secret' exists"
  else
    echo "✗ Secret '$secret' is missing!"
    exit 1
  fi
done

echo "All required secrets are present"
```

### Pod-Level Validation

```bash
# Verify secret injection in pod
kubectl exec -it [pod-name] -n ecommerce -- env | grep JWT_SECRET

# Verify secret is NOT in logs
kubectl logs [pod-name] -n ecommerce | grep -i secret | grep -v "secret-path"
```

---

## Security Best Practices

### ✓ Do's

- ✓ Use strong, randomly generated secrets (min 32 chars)
- ✓ Rotate secrets regularly (every 90 days for passwords, 365 days for keys)
- ✓ Use Vault in production with encryption at rest
- ✓ Implement RBAC to restrict secret access
- ✓ Enable audit logging for all secret access
- ✓ Use mTLS between services and Vault
- ✓ Keep `.env` files in `.gitignore`
- ✓ Use separate secrets for each environment

### ✗ Don'ts

- ✗ Never commit secrets to git (even in private repos)
- ✗ Never log secrets (sanitize logs)
- ✗ Never use default/weak secrets in production
- ✗ Never share secrets via email or chat
- ✗ Never use plaintext passwords in application code
- ✗ Never disable RBAC on secret resources
- ✗ Never store multiple versions of same secret
- ✗ Never use same secret across environments

---

## Environment-Specific Setup

### Development (Local Machine)

```bash
# Copy example env file
cp .env.example .env

# Edit with your local values
nano .env

# Load environment
export $(cat .env | xargs)

# Run services
mvn clean install
mvn spring-boot:run
```

### Local Kubernetes (minikube/Docker Desktop)

```bash
# Create secrets from YAML
kubectl apply -f k8s/16-service-secrets.yaml

# Deploy services
kubectl apply -f k8s/
```

### Staging (Kubernetes with Encryption)

```bash
# Enable etcd encryption
# Secrets encrypted at rest in etcd

# Deploy with helm
helm upgrade --install micro-ecommerce ./helm \
  -n ecommerce \
  --values values-staging.yaml
```

### Production (Vault)

```bash
# Services authenticate via Kubernetes auth
# Secrets automatically injected by Vault Agent

# Deploy with helm
helm upgrade --install micro-ecommerce ./helm \
  -n ecommerce \
  --values values-prod.yaml
```

---

## Troubleshooting

### Issue: Pods can't access secrets

**Solution**:
```bash
# Check secret exists
kubectl get secret jwt-secret -n ecommerce-secrets

# Check pod's service account has RBAC permission
kubectl get rolebinding -n ecommerce-secrets -o yaml

# Verify environment variables in pod
kubectl exec -it [pod] -- env | grep JWT_SECRET
```

### Issue: Secret rotation failed

**Solution**:
```bash
# Check Vault status
vault status

# Check policy allows secret write
vault policy read ecommerce-policy

# Verify Vault is unsealed
vault operator unseal [key]
```

### Issue: Vault pod not starting

**Solution**:
```bash
# Check logs
kubectl logs vault-0 -n vault

# Check Kubernetes auth config
vault auth list

# Reinitialize if needed
vault operator init -key-shares=5 -key-threshold=3
```

---

## References

- [Kubernetes Secrets Documentation](https://kubernetes.io/docs/concepts/configuration/secret/)
- [HashiCorp Vault Documentation](https://www.vaultproject.io/docs)
- [Spring Cloud Vault](https://spring.io/projects/spring-cloud-vault)
- [OWASP Secrets Management](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
