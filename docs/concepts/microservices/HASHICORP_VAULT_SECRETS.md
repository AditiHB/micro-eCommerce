# HashiCorp Vault & Secret Management 🔐

Secure secret management for microservices using HashiCorp Vault and Spring Cloud Vault.

---

## What is HashiCorp Vault?

HashiCorp Vault is a centralized secret and encryption management system.

### Simple Definition:

Imagine a secure vault that stores all your sensitive data:
- Database passwords
- API keys
- SSL certificates
- OAuth tokens
- SSH keys
- Encryption keys

```
┌─────────────────────────────────────────┐
│       HashiCorp Vault (Secure)          │
│                                         │
│  ├─ Database passwords                  │
│  ├─ API keys (Stripe, AWS, etc.)        │
│  ├─ OAuth tokens                        │
│  ├─ SSL certificates                    │
│  ├─ SSH keys                            │
│  └─ Encryption keys                     │
└─────────────────────────────────────────┘
        ↑           ↑           ↑
        │           │           │
   Application  Service   Batch Job
```

### NOT Safe:
```
❌ Hardcoded in code:
String password = "mysql_secret_123";

❌ In configuration files:
spring.datasource.password=mysql_secret_123

❌ In environment variables (visible to everyone):
export DB_PASSWORD=mysql_secret_123
```

### ✅ SAFE with Vault:
```
1. Request secret from Vault
2. Vault validates identity
3. Vault returns secret
4. Application uses it
5. Secret never stored on disk
```

---

## Key Features of HashiCorp Vault

| Feature | Description |
|---------|-------------|
| **Centralized** | Single source of truth for all secrets |
| **Secure Storage** | AES-256 encryption at rest |
| **Access Control** | Fine-grained permissions (who can access what) |
| **Audit Logging** | Track all secret access |
| **Rotation** | Automatic secret rotation |
| **Encryption as Service** | Encrypt/decrypt data through API |
| **Multiple Backends** | Support for AWS, Azure, Kubernetes auth |
| **Lease Management** | Secrets expire automatically |
| **High Availability** | Replicated and redundant |

---

## Vault Architecture

```
ARCHITECTURE:
═════════════════════════════════════════

                    ┌──────────────────┐
                    │ HashiCorp Vault  │
                    │   (Secure)       │
                    └────────┬─────────┘
                             │
                ┌────────────┼────────────┐
                │            │            │
                ↓            ↓            ↓
        Database    Payment Service   Inventory
        Passwords   API Keys           SSH Keys
        
        Each service authenticates and retrieves only
        the secrets it needs!
```

---

## Secret Storage Backends

```
Vault supports multiple secret storage locations:

1. Consul Backend
   └─ Encrypted key-value store

2. S3 Backend
   └─ Store in AWS S3 (encrypted)

3. File Backend
   └─ Local filesystem (for testing only!)

4. Integrated Storage (Raft)
   └─ Built-in distributed storage (recommended)
```

---

## Vault Authentication Methods

```
HOW APPLICATIONS AUTHENTICATE TO VAULT:
════════════════════════════════════════

1. USERPASS
   ├─ Username/Password
   └─ Use: Testing, manual access

2. TOKEN
   ├─ Static token
   └─ Use: Simple applications

3. KUBERNETES
   ├─ K8s service account
   └─ Use: Kubernetes pods

4. AWS IAM
   ├─ AWS IAM credentials
   └─ Use: EC2 instances, Lambda

5. JWT/OIDC
   ├─ JSON Web Token
   └─ Use: OAuth2 providers

6. APPROLE
   ├─ Application Role ID + Secret ID
   └─ Use: Microservices (recommended)
```

---

## Spring Cloud Vault Config

Spring Cloud Vault Config integrates Vault with Spring Boot applications.

### How It Works:

```
Application Startup:
    ↓
Spring Cloud Vault Client starts
    ↓
Authenticate to Vault (AppRole/K8s/etc.)
    ↓
Retrieve secrets from Vault
    ↓
Load into Spring Environment
    ↓
Application uses @Value or @ConfigurationProperties
    ↓
Secret never stored on disk! ✅
```

---

### Installation & Configuration

**Step 1: Add Dependency**
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-vault-config</artifactId>
</dependency>
```

**Step 2: Bootstrap Configuration**
```yaml
# bootstrap.yml or bootstrap.properties
spring:
  cloud:
    vault:
      host: vault.example.com
      port: 8200
      scheme: https
      
      # Authentication method (AppRole)
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
      
      # Path where secrets are stored
      kv:
        enabled: true
        backend: secret
        profile-separator: '/'
        default-context: microservices
        application-name: payment-service
```

**Step 3: Create Secret in Vault**
```bash
# Store database password in Vault
vault kv put secret/microservices/payment-service \
  spring.datasource.password="secure_password_123" \
  spring.datasource.url="jdbc:mysql://localhost/payment"
```

**Step 4: Use in Application**
```java
@Component
public class DatabaseConfig {
    
    @Value("${spring.datasource.password}")
    private String dbPassword;  // Retrieved from Vault!
    
    @Value("${spring.datasource.url}")
    private String dbUrl;       // Retrieved from Vault!
}
```

---

## Secret Management Patterns

### Pattern 1: Database Credentials

```
VAULT STORAGE:
secret/microservices/payment-service/
├─ db_host: localhost
├─ db_port: 3306
├─ db_user: payment_user
└─ db_password: ***encrypted***

SPRING BOOT:
@Component
public class DataSourceConfig {
    
    @Bean
    public DataSource dataSource(
        @Value("${db_host}") String host,
        @Value("${db_port}") int port,
        @Value("${db_user}") String user,
        @Value("${db_password}") String password
    ) {
        return DataSourceBuilder.create()
            .driverClassName("com.mysql.jdbc.Driver")
            .url("jdbc:mysql://" + host + ":" + port + "/payment")
            .username(user)
            .password(password)
            .build();
    }
}
```

---

### Pattern 2: API Keys & Tokens

```
VAULT STORAGE:
secret/microservices/payment-service/
├─ stripe_api_key: sk_live_***encrypted***
├─ aws_access_key: AKIA***encrypted***
├─ aws_secret_key: ***encrypted***
└─ oauth_client_secret: ***encrypted***

SPRING BOOT:
@Component
public class PaymentGateway {
    
    @Value("${stripe_api_key}")
    private String stripeApiKey;
    
    public void chargeCard(String cardToken, double amount) {
        // Use stripeApiKey to call Stripe API
        StripeClient.setApiKey(stripeApiKey);
        // ...
    }
}
```

---

### Pattern 3: SSL/TLS Certificates

```
VAULT STORAGE:
secret/microservices/payment-service/
├─ ssl_certificate: -----BEGIN CERTIFICATE-----
├─ ssl_private_key: -----BEGIN PRIVATE KEY-----
└─ ssl_key_password: ***encrypted***

SPRING BOOT:
server:
  ssl:
    key-store-type: PKCS12
    key-store: /tmp/keystore.p12
    key-store-password: ${ssl_key_password}
    key-alias: tomcat
```

---

### Pattern 4: Encryption Keys

```
VAULT STORAGE:
secret/microservices/payment-service/
├─ encryption_key: ***256-bit key***
├─ hmac_key: ***256-bit key***
└─ encryption_algorithm: AES-256-GCM

SPRING BOOT:
@Component
public class CryptoService {
    
    @Value("${encryption_key}")
    private String encryptionKey;
    
    public String encrypt(String data) {
        // Use encryptionKey to encrypt sensitive data
        return AES256.encrypt(data, encryptionKey);
    }
}
```

---

## Secret Rotation

### Automatic Secret Rotation

```
VAULT AUTOMATIC ROTATION:
════════════════════════════

Every 30 days (or custom interval):
    ↓
Vault generates new secret
    ↓
Vault updates secret in storage
    ↓
Applications refresh configuration
    ↓
New secret is used automatically
    ↓
Old secret is invalidated

No downtime! No manual intervention!
```

### Manual Rotation

```bash
# Generate new password
vault kv put secret/microservices/payment-service \
  spring.datasource.password="new_secure_password_456"

# Applications restart or refresh configuration
# New password is loaded from Vault
```

---

## Secret Management Best Practices

### ✅ DO's

```java
// 1. Use Vault for all secrets
@Value("${db_password}")
private String dbPassword;  // ✅ From Vault

// 2. Use Spring Cloud Config with Vault
@Configuration
@PropertySource("vault://secret/microservices/${spring.application.name}")
public class VaultConfig {
}

// 3. Rotate secrets regularly
// Vault handles this automatically

// 4. Use appropriate authentication
// AppRole for services, K8s for pods

// 5. Enable audit logging
// Track all secret access

// 6. Use short-lived tokens
// Tokens expire automatically
```

### ❌ DON'Ts

```java
// 1. Don't hardcode secrets
String password = "mysql_secret_123";  // ❌ WRONG!

// 2. Don't store in config files
// application.properties with password  ❌

// 3. Don't use environment variables for ALL secrets
export DB_PASSWORD=secret  // ❌ Visible to ps, history

// 4. Don't commit secrets to Git
// git add application.properties  ❌

// 5. Don't use same secret for all environments
// Use different secrets per environment  ✅

// 6. Don't ignore audit logs
// Monitor who accessed what
```

---

## Environment Variable Management in Production

### Question: Who Sets Secrets in Production?

```
PRODUCTION SECRET MANAGEMENT FLOW:
═══════════════════════════════════════════════════

1. PLANNING PHASE (DevOps)
   ├─ Identify all secrets needed
   ├─ Create secret management strategy
   └─ Plan Vault deployment

2. VAULT DEPLOYMENT (DevOps)
   ├─ Install and configure Vault
   ├─ Set up authentication (AppRole, K8s)
   ├─ Configure storage backend
   └─ Enable audit logging

3. SECRET CREATION (DevOps/SecOps)
   ├─ Generate secure credentials
   │  └─ DB passwords (random, 32+ chars)
   │  └─ API keys (from services)
   │  └─ SSL certs (from CA)
   ├─ Store in Vault
   ├─ Apply access policies
   └─ Document secret locations

4. APPLICATION DEPLOYMENT (DevOps/Platform Team)
   ├─ Deploy application
   ├─ Configure AppRole/K8s auth
   ├─ Spring Cloud Vault pulls secrets
   └─ Application runs with secrets from Vault

5. ONGOING (SecOps/DevOps)
   ├─ Monitor Vault usage
   ├─ Rotate secrets on schedule
   ├─ Update access policies
   └─ Review audit logs
```

---

## Who Sets What in Production?

### DevOps/Infrastructure Team
```
Responsibilities:
├─ ✅ Install and configure Vault
├─ ✅ Set up authentication methods
├─ ✅ Configure storage backend
├─ ✅ Set up replication/HA
├─ ✅ Enable audit logging
├─ ✅ Configure backup/recovery
└─ ✅ Monitor Vault health
```

### SecOps/Security Team
```
Responsibilities:
├─ ✅ Generate secure credentials
├─ ✅ Store secrets in Vault
├─ ✅ Set up access policies
├─ ✅ Review audit logs
├─ ✅ Plan secret rotation
├─ ✅ Compliance verification
└─ ✅ Security incident response
```

### Platform/Application Team
```
Responsibilities:
├─ ✅ Use Spring Cloud Vault in code
├─ ✅ Configure application authentication to Vault
├─ ✅ Reference secrets in configuration
├─ ✅ Test with Vault integration
├─ ✅ Deploy application
└─ ✅ Monitor application logs
```

---

## Environment Variables Approach (Not Recommended)

### How It Works (Old Way)

```
❌ Using Environment Variables for Secrets:

1. DevOps sets env vars on server:
   export DB_PASSWORD=mysql_secret_123
   export API_KEY=stripe_key_123
   export AWS_SECRET=aws_secret_123

2. Application reads env vars:
   String dbPassword = System.getenv("DB_PASSWORD");

3. Problems:
   ├─ Visible in: ps aux, history, logs
   ├─ No rotation: Manual process
   ├─ No audit: Can't track access
   ├─ Hard to rotate: Requires restart
   └─ No encryption: Plain text in memory
```

### Risks with Environment Variables

```
VISIBILITY PROBLEMS:
═══════════════════════════════════════

$ ps aux | grep java
root  12345  /usr/bin/java -Ddb.password=secret123
                                       ↑ VISIBLE!

$ env | grep API_KEY
API_KEY=stripe_key_123  ← VISIBLE!

$ history
export DB_PASSWORD=mysql_secret_123  ← VISIBLE!

$ /proc/[PID]/environ
DB_PASSWORD=mysql_secret_123  ← VISIBLE!

$ docker inspect [container]
"Env": ["DB_PASSWORD=mysql_secret_123"]  ← VISIBLE!
```

---

## Recommended: Vault + Spring Cloud

### Why Vault is Better

```
VAULT ADVANTAGES:
═════════════════════════════════════

✅ SECURITY
├─ AES-256 encryption at rest
├─ TLS encryption in transit
└─ No secrets in environment

✅ ACCESS CONTROL
├─ Fine-grained permissions
├─ Role-based access control (RBAC)
└─ Service-specific secrets only

✅ AUDIT LOGGING
├─ Track all secret access
├─ Who accessed what, when
└─ Why (audit trail)

✅ AUTOMATION
├─ Automatic secret rotation
├─ No manual intervention
└─ No downtime

✅ COMPLIANCE
├─ HIPAA, PCI-DSS ready
├─ Audit trails for compliance
└─ Encryption enforcement

✅ FLEXIBILITY
├─ Multiple auth methods (K8s, AWS, JWT, etc.)
├─ Dynamic secrets (auto-generated)
└─ Encryption as a service
```

---

## Production Deployment Example

### E-Commerce Payment Service

```
PRODUCTION SETUP:
═════════════════════════════════════════════

1. VAULT SETUP (DevOps)
   
   vault kv put secret/microservices/payment-service \
     spring.datasource.url="jdbc:mysql://prod-db:3306/payment" \
     spring.datasource.username="payment_user" \
     spring.datasource.password="$(openssl rand -base64 32)" \
     spring.jpa.hibernate.ddl-auto="validate" \
     stripe.api.key="sk_live_..." \
     aws.access.key="AKIA..." \
     aws.secret.key="$(openssl rand -base64 32)" \
     jwt.secret="$(openssl rand -base64 64)" \
     encryption.key="$(openssl rand -base64 32)"

2. KUBERNETES SETUP (DevOps)
   
   # Create AppRole for payment-service
   vault write auth/approle/role/payment-service \
     token_ttl=1h \
     policies="payment-service-policy"
   
   # Get Role ID and Secret ID
   vault read auth/approle/role/payment-service/role-id
   vault write -f auth/approle/role/payment-service/secret-id

3. APPLICATION DEPLOYMENT (Platform Team)
   
   # Deploy spring-boot app with:
   spring:
     cloud:
       vault:
         host: vault.prod.internal
         port: 8200
         app-role:
           role-id: ${VAULT_ROLE_ID}    # From K8s secret
           secret-id: ${VAULT_SECRET_ID}  # From K8s secret

4. RUNNING
   
   ✅ Application starts
   ✅ Spring Cloud Vault authenticates with Vault
   ✅ Retrieves all secrets from Vault
   ✅ Secrets loaded into Spring Environment
   ✅ No secrets on disk!
   ✅ Secrets rotated automatically
   ✅ Access logged and audited

5. MONITORING (SecOps)
   
   ✅ Monitor Vault logs
   ✅ Alert on unauthorized access attempts
   ✅ Review rotation status
   ✅ Compliance audit trail
```

---

## Comparison: Approaches to Secret Management

| Aspect | Hardcoded | Env Vars | Vault |
|--------|-----------|----------|-------|
| **Security** | ❌ Worst | ❌ Poor | ✅ Best |
| **Visibility** | ❌ In code | ❌ In env | ✅ Hidden |
| **Audit Trail** | ❌ None | ❌ None | ✅ Complete |
| **Rotation** | ❌ Manual | ❌ Manual | ✅ Auto |
| **Encryption** | ❌ None | ❌ None | ✅ AES-256 |
| **Access Control** | ❌ None | ❌ Basic | ✅ Fine-grained |
| **Compliance** | ❌ No | ⚠️ Partial | ✅ Yes |
| **Scalability** | ❌ No | ❌ Limited | ✅ High |

---

## Summary

| Topic | Key Point |
|-------|-----------|
| **Vault** | Centralized, secure secret storage with encryption and audit |
| **Spring Cloud Vault** | Spring integration that auto-loads secrets at startup |
| **Secret Management** | Centralized control, automatic rotation, fine-grained access |
| **Production Setup** | DevOps installs Vault, SecOps creates secrets, Apps pull from Vault |
| **Environment Variables** | Not recommended for secrets (visible, no rotation, no audit) |
| **Best Practice** | Use Vault + Spring Cloud Vault for all production secrets |

---

*Last Updated: 2026-10-03*
*HashiCorp Vault & Secret Management Best Practices! 🔐*
