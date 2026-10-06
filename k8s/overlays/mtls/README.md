# Mutual TLS between backend services

Layers onto `k8s/overlays/postgres` (mTLS is treated as part of a "real" deployment profile that
already assumes a shared database). Every backend service - `api-gateway`, `customer-service`,
`order-service`, `inventory-service`, `payment-service`, `notification-service` - gets its own
certificate. `customer-service`, `order-service`, `inventory-service` and `payment-service` require
a client certificate from every caller (they're reached via the gateway's `lb://` routes or a direct
service-to-service call). `api-gateway`'s inbound side and `notification-service`'s inbound side are
both untouched - nothing in the mesh calls either of them the way it calls the other four, and
`notification-service` is meant to stay directly, externally reachable (like Postman) - see
`docs/SMALLSTEP_PKI.md` section 11 for the full design and why Docker Compose and Kubernetes issue
certificates differently while every service consumes them the same way.

`product-service` is not part of this: it has no Deployment in `k8s/base` at all.

## Prerequisite: cert-manager

This overlay's `cluster-issuer.yaml`/`certificates.yaml` need cert-manager's CRDs already
installed and its controller `Ready` - a net-new dependency, not something any other overlay in
this repo needs. Install a pinned release (check it against your cluster's Kubernetes version;
any cert-manager v1.14+ line supports Kubernetes 1.30) and wait for it before applying this
overlay:

```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.16.2/cert-manager.yaml
kubectl wait --for=condition=Available deployment --all -n cert-manager --timeout=120s

kubectl apply -k k8s/overlays/mtls

# Confirm every certificate actually issued before trusting the deployment:
kubectl get certificate -n ecommerce
kubectl get certificate -n cert-manager
```

## What this does not cover

Same scope as `docs/SMALLSTEP_PKI.md` section 11: the nginx -> api-gateway hop, Eureka/
config-server control-plane traffic, and Kafka/Redis/Postgres are all still plain (TLS or auth
only, no mutual TLS). `product-service` is untouched (no k8s Deployment exists for it).

## Known trade-offs

- **api-gateway needs a restart after its certificate renews.** Spring Cloud Gateway 4.1's
  outbound HTTP client reads a PKCS12 keystore, which does not hot-reload like the PEM bundles
  every other service uses. cert-manager rewrites the `api-gateway-tls` Secret's `keystore.p12`
  key in place on renewal, but the running process doesn't notice - run
  `kubectl rollout restart deployment/api-gateway -n ecommerce` after a renewal (or on whatever
  cadence you're comfortable with; a 24h cert with an 8h `renewBefore` gives a wide window).
- **Eureka's `instanceId` is now the same for every replica of a service minus `POD_NAME`.**
  Advertising the stable Service hostname (required so it matches the certificate's SANs) means
  every replica shares `hostname:port`; `POD_NAME` (Downward API) keeps `instance-id` distinct so
  the registry doesn't collapse N pods into 1. Routing still works correctly either way (the
  Service VIP load-balances at L4), this only affects Eureka's own bookkeeping during a rolling
  restart.

## Verifying it actually enforces mTLS, not just configures it

From a scratch debug pod in the `ecommerce` namespace, confirm an unauthenticated call is
rejected (TLS handshake failure, not an HTTP-level 401/403 - this should fail before any
application code runs):

```bash
kubectl run mtls-probe --rm -it --image=curlimages/curl -n ecommerce -- \
  curl -v https://customer-service:8081/actuator/health
```

Then confirm the existing e2e Karate suite (`e2e-tests/`) still passes unchanged - it only ever
talks to the gateway/notification-service's public-facing side, so turning on mTLS between the
services behind them should be invisible to it.
