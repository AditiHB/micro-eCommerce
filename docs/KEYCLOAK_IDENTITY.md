# Identity and access control (Keycloak)

Every access token on this platform is issued by **Keycloak**. The gateway and the services never see a
password and never sign anything: they verify RS256 tokens against Keycloak's published keys (JWKS) and
decide what the caller may do from the roles and claims inside the token.

This replaces the earlier design, in which `customer-service` exposed `POST /api/auth/login`, every
service shared one HMAC signing secret (with a public default), and each service kept its own `users` table.

## Contents

1. [How a request is authenticated](#1-how-a-request-is-authenticated)
2. [Roles and the access matrix](#2-roles-and-the-access-matrix)
3. [Object-level rules: customers only see their own data](#3-object-level-rules)
4. [Getting a token](#4-getting-a-token)
5. [Service-to-service calls](#5-service-to-service-calls)
6. [Managing users, roles and clients](#6-managing-users-roles-and-clients)
7. [Configuration reference](#7-configuration-reference)
8. [Going to production](#8-going-to-production)

## 1. How a request is authenticated

```
client ──(1) password / auth-code+PKCE──▶ Keycloak ──▶ access token (RS256, 5 min)
client ──(2) Authorization: Bearer ──▶ API gateway   verifies signature, expiry, issuer, audience
                                         │           (rate limit, circuit breaker)
                                         └─(3) same token, untouched ──▶ service
                                                      verifies it AGAIN, then applies roles + ownership
```

* **Two checks on purpose.** The gateway rejects bad tokens at the edge; each service validates the same
  token again, so a bypassed or compromised gateway grants nothing.
* **What is checked:** signature (keys fetched from Keycloak's JWKS endpoint and cached), `exp`, `iss`
  (the public Keycloak URL), and `aud` must contain `ecommerce-api` - a token minted for another API is refused.
* **Startup is fail-fast.** `ecommerce.security.jwk-set-uri`, `issuer` and `audience` have no defaults inside
  the shared security module; a service refuses to start without them. (The `application.yml` files point
  them at the local Keycloak, which is public configuration, not a secret.)
* **Failures:** no/invalid token → `401` with `WWW-Authenticate: Bearer`; valid token, wrong role → `403`;
  somebody else's record → `404` (the existence of the id is not confirmed).

Code: `common/.../security/SecurityConfig.java` (services), `api-gateway/.../GatewaySecurityConfig.java` (edge),
`KeycloakJwtAuthenticationConverter` (roles), `AudienceValidator`, `CurrentUser` (object-level checks).

## 2. Roles and the access matrix

| Role | Who | Meant for |
| --- | --- | --- |
| `USER` | a customer | their own customer record, their own orders |
| `MANAGER` | back-office staff | cross-customer reads and operational changes |
| `ADMIN` | administrator | everything a manager can do, plus creating inventory and deleting customers |
| `SERVICE` | a machine identity (`notification-service`, `order-service`) | read customers/orders on behalf of the platform |

The URL rules are **deny by default**: an endpoint that is not listed is refused for everybody. They are
asserted exhaustively - every endpoint × every role - by `AuthorizationMatrixTest` (222 cases), so loosening a
rule fails the build. Note this matrix is a manually curated list of rules, not auto-discovered from the
controllers: a genuinely new endpoint needs its own `Rule` added here, or it is simply untested, not
unprotected (`SecurityConfig`'s own wildcard patterns, e.g. `/customers/**`, still deny-by-default it).

| Endpoint | USER | MANAGER | ADMIN | SERVICE |
| --- | :-: | :-: | :-: | :-: |
| `GET /api/customers` (list) | – | ✔ | ✔ | – |
| `GET /api/customers/{id}` | own only | ✔ | ✔ | ✔ |
| `POST/PUT /api/customers` | – | ✔ | ✔ | – |
| `DELETE /api/customers/{id}` | – | – | ✔ | – |
| `POST /api/customers/{id}/card` (add/replace card on file) | – | ✔ | ✔ | – |
| `DELETE /api/customers/{id}/card` | – | – | ✔ | – |
| `GET /api/orders`, `GET /api/orders/{id}` | own only | ✔ | ✔ | ✔ |
| `GET /api/orders/{id}/invoice` (JWE-encrypted PDF + order details) | own only | ✔ | ✔ | ✔ |
| `POST /api/orders` | for self only | ✔ | ✔ | – |
| `PUT /api/orders/{id}/status` | – | ✔ | ✔ | – |
| `GET /api/payments…` | – | ✔ | ✔ | – |
| `POST /api/payments`, `POST /api/payments/{id}/refund` | – | ✔ | ✔ | – |
| `GET /api/inventory…`, `PUT /api/inventory/{id}` | – | ✔ | ✔ | – |
| `POST /api/inventory…` (create, reserve, release) | – | – | ✔ | – |
| `GET /api/products…` | ✔ | ✔ | ✔ | ✔ |
| `POST/PUT/DELETE /api/products…` | – | ✔ | ✔ | – |
| `GET /api/notifications…` | – | ✔ | ✔ | – |
| `/actuator/**` (public port) | health + info only, nothing else | | | |

Holes this closes (from the engineering review): any logged-in user could refund any payment, set any stock
level, create a payment that marked someone else's order `COMPLETED` for $0.01, list every customer's name and
email, and list every customer's orders.

## 3. Object-level rules

URL rules say *which kind of caller* may use an endpoint; they cannot say *whose record*. That is what the
`customer_id` claim is for.

* A user is bound to one customer record by the Keycloak user attribute **`customer_id`**. Only a Keycloak
  administrator can set it (the realm's user profile makes it admin-editable only - a user cannot reassign it
  to themselves and impersonate someone else). A protocol mapper copies it into the access token.
* `CurrentUser.canAccessCustomer(id)` is true for `ADMIN`/`MANAGER`/`SERVICE`, or when the token's `customer_id`
  equals `id`. Used by:
  * `OrderController.getById` and `CustomerController.getById` → otherwise **404**;
  * `OrderController.getAll` → a `USER` is served *their own* orders only (`findByCustomerId`, with the customer
    id in the cache key), never the global list; a `USER` token with no `customer_id` gets 403;
  * `OrderController.createOrder` → `@PreAuthorize("@currentUser.canAccessCustomer(#request.customerId)")`:
    a customer cannot place an order for somebody else. The check runs *before* the circuit-breaker/retry
    advice, so a forbidden request is neither retried nor counted as a downstream failure.

Tests: `OrderOwnershipTest`, `CustomerOwnershipTest` (real filter chain), `CurrentUserTest`, and the Karate
`authorization.feature` against the live stack.

## 4. Getting a token

Keycloak: <http://localhost:8180> (admin console login = `KC_BOOTSTRAP_ADMIN_*` in your `.env`).
Realm: `ecommerce`. Issuer: `http://localhost:8180/realms/ecommerce`.

Development users and a test client are created by `infrastructure/keycloak/seed-dev.sh` (one-shot
`keycloak-seed` service) - **not** by the realm file, so they cannot reach a real deployment. Their secrets are
generated into your git-ignored `.env` by `scripts/gen-env.sh`:

| User | Role | Password variable | Notes |
| --- | --- | --- | --- |
| `karate_admin` | ADMIN | `E2E_ADMIN_PASSWORD` | |
| `karate_manager` | MANAGER | `E2E_MANAGER_PASSWORD` | |
| `karate_user` | USER | `E2E_USER_PASSWORD` | bound to customer `1` |

```bash
set -a; . ./.env; set +a
TOKEN=$(curl -s http://localhost:8180/realms/ecommerce/protocol/openid-connect/token \
  -d grant_type=password -d client_id=ecommerce-e2e -d "client_secret=$E2E_CLIENT_SECRET" \
  -d username=karate_admin -d "password=$E2E_ADMIN_PASSWORD" | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/customers
```

The password grant exists **only** for the development client `ecommerce-e2e` (automation and scripts). Real
front ends use the `ecommerce-web` public client: authorization-code flow with PKCE, no secret. Postman: run
*Authentication → Get access token* (set `keycloak_client_secret`, `username`, `password` in your environment).

Decode a token to see what services see: `echo "$TOKEN" | cut -d. -f2 | base64 -d` - expect `iss`, `aud`
(`ecommerce-api`), `realm_access.roles`, `customer_id`, `exp` five minutes out.

## 5. Service-to-service calls

Two services hold their own machine identity, each a **Keycloak client** using the OAuth2
*client-credentials* grant: it exchanges its client id and secret for a short-lived token whose service
account holds the `SERVICE` role, caches it, and attaches it to its calls (`ServiceAuthConfig`,
`ServiceAccountAuthInterceptor`). The secret is injected from the environment or Vault -
`ecommerce.service-auth.client-secret` is required and has no default.

* **`notification-service`** needs customer and order details to address notifications.
* **`order-service`** needs customer details (name, email) to render an invoice. This is the *invoice batch
  job's* identity specifically - it runs on a schedule with no caller to relay a token from, unlike this
  service's request-scoped `CustomerDirectoryClient`, which still relays whichever user's token placed the
  order. The two clients exist side by side in the same service for exactly that reason: a live request has a
  real caller to act as; a scheduled job does not.

## 6. Managing users, roles and clients

* **Realm as code:** `infrastructure/keycloak/realm-ecommerce.json` - roles, the `ecommerce-web`,
  `notification-service` and `order-service` clients, audience/role/`customer_id` mappers, password policy
  (12+ chars), brute-force lockout, 5-minute access tokens. It is imported when Keycloak starts;
  `${NOTIFICATION_CLIENT_SECRET}` and `${ORDER_CLIENT_SECRET}` are substituted from the environment, so the
  file contains no secret.
* **Add a customer login:** create the Keycloak user, assign role `USER`, and set attribute `customer_id` to the id
  of their row in `customer-service`. (Self-registration is off; the customer record and the login are linked by an
  administrator or an onboarding job.)
* **Add a role:** add it to the realm file, to `Roles.java`, to the rules in `SecurityConfig`, and to the matrix in
  `AuthorizationMatrixTest`.
* **Rotate a service's client secret:** change `NOTIFICATION_CLIENT_SECRET` or `ORDER_CLIENT_SECRET`, restart
  Keycloak (re-import with a fresh volume) or update the client in the admin console, then restart the
  matching service.

## 7. Configuration reference

| Property / variable | Where | Meaning |
| --- | --- | --- |
| `ecommerce.security.jwk-set-uri` / `SECURITY_JWK_SET_URI` | gateway, every service | Keycloak JWKS endpoint, reachable from the platform network |
| `ecommerce.security.issuer` / `SECURITY_ISSUER` | gateway, every service | Expected `iss` - Keycloak's **public** URL |
| `ecommerce.security.audience` / `SECURITY_AUDIENCE` | gateway, every service | Expected `aud` (`ecommerce-api`) |
| `ecommerce.security.customer-id-claim` | services | Claim carrying the bound customer id (`customer_id`) |
| `ecommerce.security.jwks-ssl-bundle` | gateway, services | Spring SSL bundle used to reach Keycloak over TLS ([SMALLSTEP_PKI.md](SMALLSTEP_PKI.md)) |
| `ecommerce.security.docs-public` | services | Expose Swagger UI/OpenAPI (off by default) |
| `ecommerce.service-auth.*` | notification-service, order-service | Token URL, client id, **client secret** (no default), SSL bundle |

The issuer and the JWKS host differ on purpose: tokens carry the URL a *client* used (`localhost:8180`), while
services fetch keys over the internal network (`keycloak:8080`). Keycloak runs with
`KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` to allow both.

## 8. Going to production

What the local setup deliberately simplifies:

* **`start-dev` + embedded H2** → run `start --optimized` on Postgres, behind TLS, with several replicas
  ([SMALLSTEP_PKI.md](SMALLSTEP_PKI.md) covers the certificate).
* **Password grant for automation** → remove the `ecommerce-e2e` client and the seeded users; use client
  credentials for machines and authorization-code + PKCE for people.
* **Admin bootstrap credentials in `.env`** → bootstrap once, create named admins with MFA, delete the bootstrap account.
* **Realm import on start** → manage the realm with Terraform (`keycloak` provider) or `kcadm`/`keycloak-config-cli`
  in a pipeline, with review.
* **Not covered:** MFA/WebAuthn enrolment, social login/federation, token revocation lists (tokens live 5 minutes;
  use short lifetimes plus Keycloak's session revocation), audit-log shipping.
