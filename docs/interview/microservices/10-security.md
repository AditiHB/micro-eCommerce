# Security

### Q: How does authentication work across these services — is there a shared session, or does each service
check independently?

**A:** Each service is an independent OAuth2 **resource server** — no shared session, no sticky
authentication state anywhere. Every service validates the incoming JWT itself: `SecurityConfig.java` (in
`common`, applied by every service) builds a `NimbusJwtDecoder` from a JWKS endpoint
(`http://keycloak/realms/ecommerce/protocol/openid-connect/certs` by default), and validates the token's
signature (RS256, against Keycloak's published public keys), its **issuer**, and its **audience**. This means
any service can verify a token completely on its own, with zero calls back to Keycloak or to any other
service, and zero shared mutable session state to keep consistent across replicas — a token is just as valid
whichever pod happens to receive the request.

### Q: Why validate the issuer *and* audience, not just the signature?

**A:** A valid signature only proves the token was genuinely issued by *some* trusted authority — it doesn't
prove it was issued *for this service*, or by *the realm this deployment actually trusts*. Checking `issuer`
confirms it came from this deployment's actual Keycloak realm (not some other realm the same signing
infrastructure might also serve). Checking `audience` confirms the token was actually intended for this API, not
a token legitimately issued for some *other* client/application that happens to share the same issuer — without
an audience check, a token meant for one application could be replayed against a completely different one it
was never supposed to access.

### Q: What's the difference between role-based and ownership-based authorization, and where does each show
up here?

**A:** Role-based: "is this caller allowed to do this *kind* of thing at all" — a static permission check
independent of which specific record is involved. `SecurityConfig.filterChain()` declares these as URL-pattern
matchers, e.g. `DELETE /customers/**` requires `hasRole(ADMIN)`. The whole chain is **deny-by-default**
(`anyRequest().denyAll()` as the final rule) — anything not explicitly permitted is refused, rather than
accidentally left open by omission.

Ownership-based: "is this caller allowed to act on *this specific* record" — a check against the actual data,
not just the caller's role. `CurrentUser.requireAccessToCustomer(...)`, called from `OrderController` and
`CustomerController`, enforces that a regular customer can only read/act on their *own* customer record, even
though "read a customer record" is a role they're generally permitted to do.

Both exist because they answer different questions: a role check alone would let any authenticated customer
read *anyone's* order history; an ownership check alone, without the role gate, would still need to separately
block a customer from hitting admin-only endpoints entirely.

### Q: Why does an ownership mismatch return 404, not 403?

**A:** A `403 Forbidden` on `GET /customers/17` tells an attacker "customer 17 definitely exists, you're just
not allowed to see it" — confirming the record's existence to someone who otherwise shouldn't be able to learn
anything about it. Returning `404 Not Found` instead (as `CurrentUser.requireAccessToCustomer` does) gives the
attacker no information beyond "this isn't accessible to you as this request," which from their perspective is
indistinguishable from the record not existing at all.

### Q: This repo has mTLS, JWT-based authorization, Vault, *and* Kubernetes NetworkPolicies. Isn't that
excessive — don't they overlap?

**A:** Each operates at a genuinely different layer, and a gap in one doesn't get backfilled by the others —
that's the actual argument for defense in depth, not redundancy for its own sake:

- **NetworkPolicy** (default-deny ingress/egress, explicit allow rules per service) controls *which pods can
  even open a connection to which other pods* — the coarsest layer, enforced before any application code runs
  at all.
- **mTLS** then authenticates *which service* is on each end of a connection that NetworkPolicy already
  allowed, and encrypts it.
- **JWT** authorizes *which end user, with which permissions* a specific request is acting on behalf of — this
  is true regardless of which service-to-service hop carried it.
- **Vault** (opt-in `vault` profile) protects the credentials and connection secrets everything above depends
  on — short-lived, dynamically minted database credentials via `database/creds/<service>`, rather than static
  passwords baked into config indefinitely.

A misconfigured NetworkPolicy that accidentally allowed a rogue pod to reach `order-service` would still be
stopped cold by mTLS (no valid client cert) and, even past that, by JWT validation (no valid token for a real
user) — each layer is a genuinely independent check, not a repeat of the one before it.

### Q: What's the actual difference between how secrets are delivered *with* Vault versus *without* it in
this repo?

**A:** Outside the `vault` profile (the default), secrets come from plain environment variables sourced from
Kubernetes `Secret` objects (`secretKeyRef` in each Deployment manifest) — static values, set once, rotated
manually. With Vault enabled (opt-in, and deliberately `fail-fast: true` — no silent fallback to a missing
secret), two tiers exist: static-ish values from Vault's KV store (Redis password, Flyway migration login), and
optionally **dynamic, short-lived database credentials** minted per service instance via
`database/creds/<service>` — a login that Vault can revoke or let expire on its own schedule, rather than a
password that's valid until someone remembers to rotate it. The dynamic tier is gated behind its own flag
(`VAULT_DATABASE_ENABLED`, default off) since it's a bigger operational step than just centralizing static
secrets.
