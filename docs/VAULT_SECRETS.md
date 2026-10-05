# Secrets with HashiCorp Vault

Vault is an **opt-in overlay** on top of the base stack. Without it, every secret comes from the git-ignored
`.env` that `scripts/gen-env.sh` generates (and the containers' environment). With it, services fetch their
secrets from Vault at startup, authenticate with their own identity, and receive **short-lived database logins**
that Vault creates for each running instance.

```bash
scripts/gen-env.sh                                   # once: generates the seed secrets in .env
docker compose -f docker-compose.yml -f docker-compose.vault.yml up -d --build              # H2 stack
docker compose -f docker-compose.yml -f docker-compose.vault.yml \
    --env-file .env --env-file .env.postgres --profile postgres up -d --build               # + Postgres, dynamic DB logins
```

## Contents

1. [What changes](#1-what-changes)
2. [How it is wired](#2-how-it-is-wired)
3. [What is stored where](#3-what-is-stored-where)
4. [Operating it](#4-operating-it)
5. [Rotation](#5-rotation)
6. [Differences from a production deployment](#6-differences-from-a-production-deployment)
7. [Troubleshooting](#7-troubleshooting)

## 1. What changes

| | Base stack | + Vault overlay |
| --- | --- | --- |
| Redis password, Flyway login | container environment | Vault KV (`secret/application`, `secret/<service>`); **removed from the container environment** (`docker inspect` shows nothing) |
| Runtime database login | one long-lived owner role per service, password in env | a **new role per instance**, DML-only, 12h lease (max 7 days), revoked when the lease ends |
| Schema changes (Flyway) | the same owner role | the owner role, from Vault KV - the running service never holds DDL rights |
| How a service proves who it is | n/a | AppRole (role-id + secret-id mounted as files) |
| If Vault is down at startup | n/a | the service **fails to start** - there is no fallback to a default |

## 2. How it is wired

```
vault-init (one shot, runs on every `up`)
   ├─ first run: init (1 key share) · unseal · audit log · KV v2 · database engine · AppRole
   │             policy per service · seed KV from .env · AppRole credentials → ./.vault/approle/<svc>/
   │             operator token (orphan, policy `admin`) → ./.vault/admin-token · REVOKE the root token
   └─ every run: unseal if restarted · (re)apply the database roles if Postgres is up

service (profile `vault` active)
   application-vault.yml  ── spring.config.import: configtree:/run/secrets/ , vault://
        1. configtree exposes spring.cloud.vault.app-role.role-id / secret-id   (files in /run/secrets)
        2. vault:// logs in with AppRole → token (1h, renewable to 4h)
        3. reads  secret/application      → spring.data.redis.password
                  secret/<service>        → db.migration.username / password, client secrets
        4. reads  database/creds/<service> → vault.db.username / vault.db.password  (new DB role)
```

* **The import is declared in the profile file** (`common/src/main/resources/application-vault.yml`, and the
  gateway's own copy) rather than in an environment variable: Spring Boot resolves an environment-supplied
  `spring.config.import` *before* it reads any `application*.yml`, so Vault settings in those files (AppRole,
  role, URI) would not exist yet and it would fall back to token authentication.
* **Property precedence does the switching.** `application-postgres.yml` resolves the datasource login as
  `${vault.db.username:${DB_USERNAME:}}`: Vault's value wins when present, the environment is the fallback in
  the base stack. The Flyway login is `${db.migration.username:${vault.db.username:${DB_USERNAME:}}}`.
* **Policies** (`infrastructure/vault/policies/`): a service can read `secret/application`, **its own**
  `secret/<service>`, and `database/creds/<service>` - nothing else. It cannot read another service's secrets or
  mint another service's database login. The operator policy cannot seal Vault or change audit devices.
* **Postgres side** (`infrastructure/postgres/init-multiple-postgres-databases.sh`): per service a LOGIN owner
  role (`<svc>_owner`, runs migrations) and a NOLOGIN `<svc>_app` group role with default DML privileges on whatever
  the owner creates. Vault's own login `vault_admin` has `CREATEROLE` and `ADMIN OPTION` on those group roles only;
  every role it mints is `IN ROLE <svc>_app`. `CONNECT` on each database is revoked from `PUBLIC`, so one
  service's login cannot open another service's database (verified: `order_owner` → `customer_db` is refused).

## 3. What is stored where

| Path | Keys | Read by |
| --- | --- | --- |
| `secret/application` | `spring.data.redis.password` | every service and the gateway |
| `secret/<service>` (customer, order, inventory, payment, notification, product) | `db.migration.username`, `db.migration.password` | that service |
| `secret/notification-service` (extra) | `ecommerce.service-auth.client-secret` | notification-service |
| `database/creds/<service>` | dynamic `username` / `password` | that service |
| `auth/approle/role/<service>` | role-id / secret-id | issued to the service at bootstrap |

Not in Vault: Keycloak's admin password and the notification client secret *as Keycloak receives them* (Keycloak
has no Vault integration without extensions), Postgres' own superuser password, the Vault seed secrets in `.env`.

## 4. Operating it

```bash
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=$(cat .vault/admin-token)          # operator token, policy `admin` (root was revoked)

vault status
vault kv get secret/order-service
vault list auth/approle/role
vault read database/creds/order-service               # mint a throwaway DB login (revoke: vault lease revoke <id>)
vault lease lookup -accessor ...                       # inspect leases
docker compose exec vault tail -f /vault/logs/audit.log      # every request, who/what/when
```

* **Restart:** Vault comes back sealed; `docker compose ... up -d` re-runs `vault-init`, which unseals it.
* **Start over:** `docker compose ... down -v && rm -rf .vault` wipes Vault's data and its bootstrap files.
* **Break glass:** with the unseal key (`.vault/unseal-key`) you can generate a new root token
  (`vault operator generate-root`); the admin policy deliberately cannot do what only root should.

## 5. Rotation

* **Database logins** rotate by themselves: every instance holds a lease; Spring Cloud Vault renews it. The lease
  has a maximum (7 days here), after which the login stops working - **recycle instances at least that often**
  (any deploy does). Long-lived pools that must survive the maximum need Vault Agent or a restart policy; this repo
  does not implement live re-keying of a running connection pool.
* **Redis password / Flyway login / client secrets:** `vault kv put secret/...` a new value, then restart the
  service(s) that read it (values are read at startup). Change the consumer (Redis server, Postgres role,
  Keycloak client) in the same maintenance step.
* **AppRole secret-id:** `vault write -f auth/approle/role/<svc>/secret-id`, write it to
  `.vault/approle/<svc>/spring.cloud.vault.app-role.secret-id`, restart the service. Revoke the old one with
  `vault write auth/approle/role/<svc>/secret-id/destroy secret_id=...`.
* **Root database login (`vault_admin`):** `vault write -f database/rotate-root/<svc>-service` lets Vault change the
  password so that nobody - including you - knows it.

## 6. Differences from a production deployment

| Here (local) | Production |
| --- | --- |
| 1 key share, unseal key stored on disk (`.vault/`) | 5 shares / threshold 3 held by different people, or **auto-unseal** with a cloud KMS / HSM |
| 1 Raft node | 3 or 5 nodes across failure domains, snapshots shipped off-site |
| HTTP on the compose network (or TLS via the [step-ca overlay](SMALLSTEP_PKI.md#vault-over-tls)) | TLS always; Vault's own cert from your PKI |
| AppRole with a non-expiring secret-id on a bind mount ("secret zero") | **Kubernetes auth** (service-account JWT, below) or cloud IAM auth; response-wrapped, single-use secret-ids if AppRole is unavoidable |
| `.env` seeds the secrets once | secrets are created in Vault by their owners / generated by the database engine; nothing is seeded from a file |
| Audit log in a container volume | audit device shipped to a SIEM; alert on denied requests and root-token use |

**On Kubernetes** services authenticate with their own service account instead of a mounted secret: enable the
Kubernetes auth method, bind a role per service to its `ServiceAccount`, and set `VAULT_AUTH_METHOD=KUBERNETES`
(`application-vault.yml` already supports it):

```bash
vault auth enable kubernetes
vault write auth/kubernetes/config kubernetes_host=https://kubernetes.default.svc
vault write auth/kubernetes/role/order-service \
    bound_service_account_names=order-service-sa bound_service_account_namespaces=ecommerce \
    policies=order-service ttl=1h
```

## 7. Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `Config data location 'vault://' does not exist` | Vault profile not active, or the starter is missing from the image - rebuild (`--build`) |
| `Cannot create authentication mechanism for TOKEN` | the import ran before the Vault settings were visible - keep the import in `application-vault.yml` |
| `permission denied` from `vault-init` when configuring the database | the operator token must be an **orphan**; child tokens die with the revoked root |
| `local node not active but active cluster node not found` | Raft leader election not finished after unseal; `vault-init` waits for it |
| service exits at startup: *403 permission denied on secret/…* | its policy does not cover that path - check `infrastructure/vault/policies/` |
| `FATAL: role "v-approle-…" does not exist` after days | the dynamic login reached its 7-day maximum - restart the service |
