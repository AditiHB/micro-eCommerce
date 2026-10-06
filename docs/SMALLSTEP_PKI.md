# Certificate management with smallstep (step-ca)

An internal certificate authority for the platform, built on [smallstep](https://smallstep.com/docs/step-ca)
`step-ca` and `step-cli`. It issues **short-lived (24 h) certificates** and renews them automatically, so there
are no certificate files to copy around, no years-long expiry dates to forget, and no private keys in git (the
earlier repo committed a TLS private key and an Nginx session-ticket key; see
[SECURITY_HARDENING.md](SECURITY_HARDENING.md)).

```bash
scripts/gen-env.sh                                  # once (creates STEP_CA_PASSWORD among others)
docker compose -f docker-compose.yml -f docker-compose.pki.yml --profile https up -d --build
```

## Contents

1. [What you get](#1-what-you-get)
2. [Trust the CA on your machine](#2-trust-the-ca-on-your-machine)
3. [Day-to-day commands](#3-day-to-day-commands)
4. [Add a certificate for a new workload](#4-add-a-certificate-for-a-new-workload)
5. [Renewal and reloading](#5-renewal-and-reloading)
6. [Provisioners: who may ask for a certificate](#6-provisioners)
7. [Revocation and expiry](#7-revocation-and-expiry)
8. [Using the certificates from Spring Boot](#8-using-the-certificates-from-spring-boot)
9. [Vault over TLS](#vault-over-tls)
10. [Kubernetes (cert-manager + step-issuer)](#10-kubernetes)
11. [Mutual TLS between services](#11-mutual-tls-between-services)
12. [Backup, rotation and production hardening](#12-backup-rotation-and-production-hardening)
13. [What this does not cover](#13-what-this-does-not-cover)
14. [Troubleshooting](#14-troubleshooting)

## 1. What you get

```
step-ca  (Smallstep CA 0.30)           root CA + intermediate CA, keys in volume step-ca-data
   │   provisioners:  admin (JWK, password = STEP_CA_PASSWORD)   acme (ACME)
   │
   ├─ pki-init        one-shot: issues nginx / keycloak / vault certificates (24h, ECDSA P-256),
   │                  publishes ca/root_ca.crt, ca/intermediate_ca.crt, ca/ca-bundle.crt
   ├─ pki-truststore  one-shot: builds ca/truststore.p12 (PKCS12) for Java clients that cannot read PEM
   └─ pki-renewer     daemon:   `step ca renew --daemon` for every certificate, at ~2/3 of its lifetime

nginx     https://localhost          serves its step-ca certificate (profile `https`)
keycloak  https://localhost:8443     serves its step-ca certificate; gateway + services verify it
vault     (optional overlay)         TLS listener from its step-ca certificate
```

| File | Purpose |
| --- | --- |
| `docker-compose.pki.yml` | step-ca, pki-init, pki-truststore, pki-renewer, and TLS wiring for nginx / Keycloak / services |
| `docker-compose.vault-tls.yml` | Vault over TLS (needs the Vault and PKI overlays) |
| `infrastructure/pki/issue-certs.sh` | what gets issued, with which names - **edit here to add a workload** |
| `infrastructure/pki/renew.sh` | the renewal daemons |
| `infrastructure/vault/vault-tls.hcl` | Vault's TLS listener |

Volumes: `step-ca-data` (the CA - **its secrets and password are the crown jewels**) and `pki-certs` (issued
certificates and the public trust bundle). Each workload mounts **only its own directory** (volume subpath),
read-only; application services mount only `ca/` and never see any private key.

## 2. Trust the CA on your machine

Copy the root certificate out of the volume and use it as a trust anchor:

```bash
docker compose -f docker-compose.yml -f docker-compose.pki.yml cp pki-renewer:/certs/ca/root_ca.crt ./root_ca.crt
curl --cacert root_ca.crt https://localhost/health                                   # nginx
curl --cacert root_ca.crt https://localhost:8443/realms/ecommerce/.well-known/openid-configuration
step certificate fingerprint root_ca.crt     # compare with: docker exec step-ca step certificate fingerprint /home/step/certs/root_ca.crt
```

Add it to your OS/browser trust store only on a development machine (Windows: `certutil -addstore -user Root root_ca.crt`;
macOS: Keychain Access → System → Always Trust; Linux: `update-ca-certificates`). **Never** distribute this root
to anyone else's machine - it is a development CA.

With `step-cli` installed locally you can bootstrap trust and get a configured client in one go:

```bash
step ca bootstrap --ca-url https://localhost:9000 --fingerprint "$(docker exec step-ca step certificate fingerprint /home/step/certs/root_ca.crt)"
step ca health
```

## 3. Day-to-day commands

Run `step` inside the CA container (it is already configured for its own CA):

```bash
docker exec step-ca step ca health                                   # → ok
docker exec step-ca step ca provisioner list                         # admin (JWK) + acme (ACME)

# What is a certificate, who signed it, how long is left?
docker exec pki-renewer step certificate inspect /certs/nginx/tls.crt --short
docker exec pki-renewer step certificate verify  /certs/nginx/tls.crt --roots /certs/ca/root_ca.crt
echo | openssl s_client -connect localhost:443 -CAfile root_ca.crt 2>/dev/null | openssl x509 -noout -subject -issuer -dates

# Renew one certificate right now (normally the renewer does this)
docker exec pki-renewer step ca renew /certs/nginx/tls.crt /certs/nginx/tls.key --force \
    --ca-url https://step-ca:9000 --root /certs/ca/root_ca.crt
```

## 4. Add a certificate for a new workload

1. Append one line to `infrastructure/pki/issue-certs.sh` (first name is the common name, the rest are SANs):

   ```sh
   issue order-service order-service localhost 127.0.0.1
   ```
2. Add its name to the `for name in …` list in `infrastructure/pki/renew.sh`.
3. Mount **only that directory** into the workload, read-only, using a volume subpath:

   ```yaml
   volumes:
     - type: volume
       source: pki-certs
       target: /certs/order-service
       read_only: true
       volume: { subpath: order-service }
   ```
4. `docker compose -f docker-compose.yml -f docker-compose.pki.yml up -d pki-init pki-renewer <workload>`.

Or issue a one-off certificate by hand with the same command the script uses:

```bash
docker run --rm -v micro-ecommerce_step-ca-data:/ca:ro -v "$PWD:/out" --network micro-ecommerce_default \
  -e STEP_CA_PASSWORD smallstep/step-cli:0.31.0 sh -c '
    printf %s "$STEP_CA_PASSWORD" > /tmp/pw
    step ca certificate my-tool.local /out/tls.crt /out/tls.key \
      --ca-url https://step-ca:9000 --root /ca/certs/root_ca.crt \
      --provisioner admin --provisioner-password-file /tmp/pw \
      --san my-tool.local --san 127.0.0.1 --not-after 24h'
```

Lifetime: the CA default is 24 h and its maximum is 24 h unless you change the provisioner's claims
(`maxTLSCertDuration`). Short lifetimes are a feature - see [revocation](#7-revocation-and-expiry).

## 5. Renewal and reloading

`pki-renewer` runs one `step ca renew --daemon` per certificate. The daemon renews at roughly **two thirds of the
lifetime** (≈ 16 h into 24 h, with jitter) and authenticates to the CA with the certificate it is about to replace
(mTLS) - it needs no password and no provisioner. Files are rewritten in place. Consumers pick the new certificate
up on their own:

| Consumer | How it reloads |
| --- | --- |
| Keycloak | re-reads its PEM files periodically (`https-certificate-reload-period`, default 1 h) |
| nginx | `nginx -s reload` every 6 h, from the container's own command (see `docker-compose.pki.yml`) |
| Vault | `SIGHUP` every 6 h (`docker-compose.vault-tls.yml`) |
| Spring Boot (when it *serves* TLS) | SSL bundle with `reload-on-update: true` - [section 8](#8-using-the-certificates-from-spring-boot) |

If the stack was down for more than the certificate lifetime, a renewal can no longer be authenticated: simply
`up` again - `pki-init` re-issues everything.

## 6. Provisioners

A **provisioner** defines *who may obtain a certificate and how they prove it*. This setup creates two:

* **`admin` (JWK)** - used by `pki-init`. Proof = the provisioner password (`STEP_CA_PASSWORD`). Fine for local
  bootstrap; in production never give a shared password to workloads.
* **`acme`** - the ACME protocol (RFC 8555), the same one Let's Encrypt uses, served at
  `https://localhost:9000/acme/acme/directory`. Any ACME client (certbot, Caddy, Traefik, cert-manager) can get
  certificates automatically. The CA validates control of the name (HTTP-01/TLS-ALPN-01 need the CA to reach the
  client by its DNS name).

```bash
# ACME from inside the compose network, e.g. a service named "my-app"
step ca certificate my-app my-app.crt my-app.key --acme https://step-ca:9000/acme/acme/directory --root root_ca.crt
certbot certonly --standalone -d my-app --server https://step-ca:9000/acme/acme/directory
```

For anything beyond a laptop, add one provisioner **per kind of workload** instead of sharing `admin`:

```bash
docker exec -it step-ca step ca provisioner add ci --type JWK --create          # CI pipelines, own password
docker exec -it step-ca step ca provisioner add k8s --type OIDC \               # people/automation via your IdP
    --client-id … --client-secret … --configuration-endpoint https://idp/.well-known/openid-configuration
docker exec -it step-ca step ca provisioner add hosts --type X5C --x5c-root root.crt   # machines that already hold a cert
docker exec -it step-ca step ca provisioner update acme --require-eab               # ACME needs external account binding
```

Restrict a provisioner with **claims and templates** (maximum lifetime, allowed SANs). A workload should be able
to obtain a certificate only for *its own* names.

## 7. Revocation and expiry

The default model here is **passive revocation by short lifetime**: a certificate valid for 24 h that is stolen
stops working within a day, with nothing to publish or fetch. Smallstep also supports active revocation:

```bash
step ca revoke --cert tls.crt --key tls.key            # revoke with the certificate's own key (passive CRL entry)
step ca revoke <serial> --provisioner admin --reason "key compromised"   # revoke by serial
```

Active revocation only matters if clients check it (CRL/OCSP). Browsers and most servers do not for private CAs,
which is why this setup relies on expiry (nginx has OCSP stapling **off** for the same reason - the old config
pointed at a public Let's Encrypt responder that can never know about a private CA).

## 8. Using the certificates from Spring Boot

**Verify a server** (what the gateway and services do for Keycloak): a PEM trust bundle as an SSL bundle.

```yaml
spring:
  ssl:
    bundle:
      pem:
        pki:
          truststore:
            certificate: file:/certs/ca/ca-bundle.crt
ecommerce:
  security:
    jwks-ssl-bundle: pki          # JWKS fetched from https://keycloak:8443 using this trust
  service-auth:
    ssl-bundle: pki               # client-credentials token endpoint, same trust
```

(`docker-compose.pki.yml` sets exactly this through `SPRING_SSL_BUNDLE_PEM_PKI_TRUSTSTORE_CERTIFICATE`,
`ECOMMERCE_SECURITY_JWKSSSLBUNDLE` and `ECOMMERCE_SERVICEAUTH_SSLBUNDLE`.)

**Serve TLS** from a service, picking up renewed certificates without a restart - and, for mutual
TLS, require and verify the caller's own certificate too (the real `mtls` bundle every backend
service's `application-mtls.yml` defines - see [section 11](#11-mutual-tls-between-services)):

```yaml
server:
  ssl:
    bundle: mtls
    client-auth: need
spring:
  ssl:
    bundle:
      pem:
        mtls:
          reload-on-update: true
          keystore:
            certificate: file:/certs/order-service/tls.crt
            private-key: file:/certs/order-service/tls.key
          truststore:
            certificate: file:/certs/ca/ca-bundle.crt
```

Spring Cloud Vault can only read a Java keystore, which is why `pki-truststore` also builds
`ca/truststore.p12` (password `changeit` - it protects the integrity of public certificates, not a secret).

## Vault over TLS

Run the Vault overlay together with the PKI overlay and the TLS add-on:

```bash
docker compose -f docker-compose.yml -f docker-compose.pki.yml -f docker-compose.vault.yml \
    -f docker-compose.vault-tls.yml --profile https up -d --build
```

Vault's listener then uses `/certs/vault/tls.{crt,key}`; `vault-init` and the services verify it against the
platform CA (`VAULT_CACERT`, and `spring.cloud.vault.ssl.trust-store` = the PKCS12 truststore).

## 10. Kubernetes

Kubernetes does **not** run smallstep/step-ca at all - it uses **cert-manager** with its own
built-in self-signed→CA issuer chain instead (a separate, Kubernetes-native CA from Compose's
step-ca, not the same trust root; the two environments never need to share one, the same way
Keycloak-in-Compose and Keycloak-in-Kubernetes are already independent deployments). This is a
deliberate simplification over also running smallstep's `step-certificates`/`step-issuer` Helm
charts inside the cluster: cert-manager's native CA issuer does the identical job (short-lived
leaf certs, automatic renewal by rewriting the `Secret` in place, no daemon needed since the
kubelet syncs a mounted Secret volume within ~60-90s) with one fewer moving part, and it's the de
facto standard tool for this regardless.

See `k8s/overlays/mtls/cluster-issuer.yaml` for the bootstrap chain (a self-signed `Issuer`, a root
CA `Certificate`, then a `ClusterIssuer` of kind `CA` referencing that root) and
`k8s/overlays/mtls/certificates.yaml` for the per-service leaf `Certificate`s - this is the actual,
tested implementation referenced from [section 11](#11-mutual-tls-between-services), not a sketch.
`k8s/overlays/mtls/README.md` has the install steps (cert-manager is a prerequisite, installed from
its own release manifest, not via Helm - nothing else in this project's Kubernetes path uses Helm).

One path difference `application-mtls.yml` has to account for: cert-manager bundles `ca.crt` into
the *same* per-service Secret as `tls.crt`/`tls.key` (every CA-issuer certificate gets one), so
there's no separate shared `ca/` volume the way Compose's `pki-certs` volume has one. The
truststore path is an overridable property (`ECOMMERCE_SECURITY_MTLS_TRUSTSTORE`, default
`file:/certs/ca/ca-bundle.crt` matching Compose) - `k8s/overlays/mtls/kustomization.yaml` overrides
it per service to `file:/certs/<service>/ca.crt`, its own mounted Secret.

## 11. Mutual TLS between services

Enabled via the `mtls` Spring profile. Each of `api-gateway`, `customer-service`, `order-service`,
`inventory-service`, `payment-service` and `notification-service` gets its own certificate.
`customer-service`, `order-service`, `inventory-service` and `payment-service` serve TLS with
`server.ssl.client-auth=need` and a PEM SSL bundle trusting `ca-bundle.crt` (see each service's
`application-mtls.yml`) - they're all reached through the gateway's `lb://` routes or a direct
service-to-service call. `notification-service` is a one-way exception: nothing in the mesh calls
it (it's reached directly and externally, like Postman - see its own `application-mtls.yml`), so
its inbound side stays plain HTTP; it only uses its certificate as a *client* identity for its one
outbound call to customer-service. `api-gateway`'s inbound side is untouched too (see below).
`product-service` is not part of any of this - it has no Kubernetes Deployment at all, and isn't in
Compose's PKI-patched service list either.

Activate it on top of whatever profile is already active:

```bash
# Docker Compose - docker-compose.pki.yml already appends ",mtls" to SPRING_PROFILES_ACTIVE
# for the 6 patched services; just bring the pki overlay up.
docker compose -f docker-compose.yml -f docker-compose.pki.yml --profile https up -d --build

# Kubernetes - see k8s/overlays/mtls/README.md (cert-manager is a prerequisite there)
kubectl apply -k k8s/overlays/mtls
```

What this took, for reference (all now wired up, not just planned):

1. Each service's own certificate (Docker Compose: `infrastructure/pki/issue-certs.sh`/`renew.sh`,
   extended from the nginx/keycloak/vault pattern. Kubernetes: `cert-manager` `Certificate` resources
   in `k8s/overlays/mtls/certificates.yaml`, issued by a self-signed `ClusterIssuer` bootstrapped in
   `cluster-issuer.yaml` - a separate, Kubernetes-native CA from Compose's step-ca, not the same
   trust root). Each service serves TLS with `server.ssl.bundle: mtls` + `client-auth: need` and a PEM
   SSL bundle trusting `ca-bundle.crt`.
2. **Probes and metrics stay off the TLS port** - every service exposes health and Prometheus on a
   separate, internal-only *management port* (9080-9086), kept plain HTTP by explicitly setting
   `management.server.ssl.enabled: false` in each service's `application-mtls.yml`. Confirmed live
   that this explicit override is required, not optional: Spring Boot's separate management server
   *inherits* `server.ssl` by default when `management.server.ssl` is left unset entirely - it does
   not default to plain HTTP the way a first read of the reference docs suggests. Without the
   override, the management port's actuator health endpoint (what every k8s liveness/readiness
   probe hits) would also demand a client certificate and every probe would fail.
3. Eureka registration advertises the secure port and a stable hostname instead of a pod IP
   (`eureka.instance.secure-port-enabled=true`, `non-secure-port-enabled=false`,
   `prefer-ip-address=false`, `hostname=<bare service name>`) - the hostname part matters more in
   Kubernetes than Compose: a certificate's SANs can only name a stable DNS name, never a pod's
   dynamic IP, and `EUREKA_INSTANCE_PREFER_IP_ADDRESS=true` (the base k8s profile's default) wins
   over this profile's YAML in Spring's property precedence unless the `mtls` overlay's patches
   explicitly override it back to `false` as an env var too - see `k8s/overlays/mtls/kustomization.yaml`.
4. Spring Cloud Gateway (4.1) cannot take a Spring SSL bundle for its outbound HTTP client: it needs a
   PKCS12 keystore (`spring.cloud.gateway.httpclient.ssl.key-store*`), and that keystore does not
   hot-reload on renewal - `api-gateway` needs a restart after each renewal (both overlays document
   this; Compose rebuilds the keystore hourly via `step certificate p12`, Kubernetes via
   `keystores.pkcs12.create: true` natively on the `Certificate`, but neither makes the running
   process pick it up without a restart).
5. `RestClient`-based clients that call another mTLS-protected service (`order-service`'s
   `CustomerDirectoryClient`, `notification-service`'s `CustomerClient`) use the new SSL-aware
   overload of `common`'s `RestClients.builder(...)`, looking up a bundle named `mtls` from Spring's
   `SslBundles` registry when present.

## 12. Backup, rotation and production hardening

* **Back up** `step-ca-data`: `certs/` (public), `secrets/` (the encrypted root and intermediate keys) and
  `db/` (issued-certificate records), plus the password. Without `secrets/` and the password the CA cannot be
  restored; with them, anyone can issue certificates - store them like a root key.
* **Root key offline.** In production create the root offline (`step ca init`/`step certificate create --profile root-ca`),
  keep it on an HSM or an air-gapped machine, and run only the **intermediate** online. The local setup keeps both
  in one volume for convenience.
* **Intermediate/root rotation:** issue the new intermediate from the root, run both in the trust bundle during
  the overlap (`ca-bundle.crt` already holds root + intermediate), then retire the old one after the longest
  certificate lifetime has passed.
* **Remote management / admin provisioners:** start the CA with `DOCKER_STEPCA_INIT_REMOTE_MANAGEMENT=true` to manage
  provisioners over the API instead of editing `ca.json`.
* **Dedicated password per secret:** here `STEP_CA_PASSWORD` protects the CA keys *and* is the `admin`
  provisioner's password. Split them in production.
* **Database:** the default Badger DB is single-node; use Postgres/MySQL for an HA CA.

## 13. What this does not cover

* TLS for Kafka, Redis and Postgres, and the nginx -> api-gateway hop (see `upstream.conf`).
* `product-service` in the mTLS rollout ([section 11](#11-mutual-tls-between-services)) - it has no
  Kubernetes Deployment at all, and was never part of Compose's PKI-patched service list.
* Client-certificate authentication of people/devices (the X5C and SSH provisioners can do it).
* SSH certificates (`step ssh`), a feature of smallstep not used here.
* Public trust: this CA is private. Browsers outside your machines will not trust it; for a public site use
  Let's Encrypt (or your cloud CA) at the edge and keep step-ca for *internal* traffic.

## 14. Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `pki-init` fails with `certificate request failed … authorization` | wrong password - `STEP_CA_PASSWORD` changed after the CA volume was created. Either restore the old value or recreate the CA (`docker compose … down -v`) |
| Services log `PKIX path building failed` reaching Keycloak | the service does not trust the platform CA: check `SPRING_SSL_BUNDLE_PEM_PKI_TRUSTSTORE_CERTIFICATE` and that `/certs/ca/ca-bundle.crt` is mounted |
| Issuer mismatch (`The iss claim is not valid`) | tokens carry the URL the client used. With the PKI overlay it is `https://localhost:8443/realms/ecommerce` (set in the overlay); tokens obtained from `http://localhost:8180` have a different issuer |
| Browser warns "not secure" | the root certificate is not in your trust store ([section 2](#2-trust-the-ca-on-your-machine)) |
| `x509: certificate is valid for …, not …` | the name you used is not in the certificate's SANs - add it in `issue-certs.sh` and re-run `pki-init` |
| nginx shows the old certificate after renewal | it reloads every 6 h; force it: `docker compose … exec nginx nginx -s reload` |
| `step: command not found` on the host | use `docker exec step-ca step …`, or install `step-cli` locally |
