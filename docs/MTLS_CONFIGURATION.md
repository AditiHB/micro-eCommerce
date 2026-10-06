# mTLS (Mutual TLS) Configuration

Mutual TLS between backend services is implemented. The authoritative description is
**`docs/SMALLSTEP_PKI.md` section 11** ("Mutual TLS between services") - this file is now a short
pointer plus the history of why an earlier attempt was removed, not a second copy of the design.

## Where to actually look

- **Design and how to activate it**: `docs/SMALLSTEP_PKI.md` section 11.
- **Docker Compose**: `infrastructure/pki/issue-certs.sh`/`renew.sh`, `docker-compose.pki.yml`.
- **Kubernetes**: `k8s/overlays/mtls/` (`kustomization.yaml`, `cluster-issuer.yaml`,
  `certificates.yaml`, `README.md` - prerequisites, trade-offs, and how to verify enforcement).
- **Application config**: each service's `application-mtls.yml`; the shared SSL-aware overload of
  `common`'s `RestClients.builder(...)` used by the real cross-service callers (`order-service`'s
  `CustomerDirectoryClient` and `CatalogClient`, `notification-service`'s `CustomerClient`).

## History: why the first attempt was removed

An earlier implementation shipped broken Kubernetes manifests, caught during a later rework and
deliberately cut rather than patched (see `docs/KUBERNETES_DEPLOYMENT.md`'s "what this round
didn't cover" at the time) because the audit found it broken on multiple independent axes, not
one typo:

- Every `Certificate` in the old `k8s/13-service-certificates.yaml` was issued for
  `*.default.svc.cluster.local` - the wrong namespace (this project deploys to `ecommerce`, never
  `default`). TLS hostname verification would have failed for all of them. The current
  `k8s/overlays/mtls/certificates.yaml` issues every certificate in `ecommerce` explicitly.
- `SERVER_SSL_KEY_STORE` pointed at a `.crt` file (a PEM certificate) while
  `SERVER_SSL_KEY_STORE_TYPE` was `PKCS12` - a `.crt` isn't a PKCS12 keystore, and nothing
  converted cert-manager's `tls.crt`/`tls.key` output into one. Spring Boot would have failed SSL
  initialization on startup even with the namespace fixed. The current `application-mtls.yml`
  files use Spring Boot's PEM-based `SslBundle` (`spring.ssl.bundle.pem.mtls.*`), which reads
  `tls.crt`/`tls.key` directly - no keystore-type mismatch, because there's no Java keystore
  involved for the five services that only serve PEM-based TLS.
- The old `k8s/15-deployments-mtls-updates.yaml` wasn't really a manifest to apply - its own header
  comment said it documented updates meant to be hand-merged into the plain-HTTP deployment files -
  but it shipped full `Deployment` objects sharing those same names, so `kubectl apply -f k8s/`
  applied both and silently let this one win by filename sort, switching production-looking HTTPS
  config live without anyone asking for it. The current implementation is an explicit, separate,
  opt-in overlay (`kubectl apply -k k8s/overlays/mtls`) using Kustomize strategic-merge patches, the
  same pattern `k8s/overlays/postgres` already uses - nothing applies it by accident.

The one piece that genuinely needed solving rather than just fixing a mistake - converting a PEM
certificate into a PKCS12 keystore for Spring Cloud Gateway's outbound client, which can't take a
PEM `SslBundle` - is handled natively now: `step certificate p12` in Docker Compose, cert-manager's
`spec.keystores.pkcs12.create: true` in Kubernetes. Neither needed a hand-rolled init container.
