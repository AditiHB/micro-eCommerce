#!/bin/sh
# Issues the platform's TLS certificates from the internal smallstep CA (step-ca).
# Runs as the one-shot `pki-init` container on every `docker compose up`; certificates are short-lived
# (24h) and kept fresh afterwards by the `pki-renewer` daemon, so re-issuing here is cheap and safe.
#
# Output layout under /certs (the `pki-certs` volume):
#   ca/root_ca.crt, ca/intermediate_ca.crt, ca/ca-bundle.crt   public CA certificates (trust anchors)
#   <name>/tls.crt, <name>/tls.key                             leaf certificate chain + private key
#
# Add a workload by appending to the issue list at the bottom. Each workload mounts only its own
# directory (volume subpath) read-only; private keys never leave the volume and are never mounted by
# the application services.
set -eu

CA_URL="${CA_URL:-https://step-ca:9000}"
CA_DIR=/ca
OUT=/certs
PASSWORD_FILE=/run/secrets/step_ca_password
PROVISIONER="${PROVISIONER:-admin}"
LIFETIME="${CERT_LIFETIME:-24h}"

log() { echo "[pki-init] $*"; }

ROOT="$CA_DIR/certs/root_ca.crt"
[ -s "$ROOT" ] || { log "root certificate not found at $ROOT"; exit 1; }
FINGERPRINT="$(step certificate fingerprint "$ROOT")"
log "CA root fingerprint: $FINGERPRINT"

mkdir -p "$OUT/ca"
cp "$ROOT" "$OUT/ca/root_ca.crt"
cp "$CA_DIR/certs/intermediate_ca.crt" "$OUT/ca/intermediate_ca.crt"
cat "$OUT/ca/root_ca.crt" "$OUT/ca/intermediate_ca.crt" > "$OUT/ca/ca-bundle.crt"
chmod 644 "$OUT"/ca/*.crt

# issue <name> <san>...   (the first SAN is also the certificate's common name)
issue() {
  name="$1"; shift
  mkdir -p "$OUT/$name"
  set -- "$@"
  san_args=""
  for san in "$@"; do san_args="$san_args --san $san"; done
  log "issuing $name ($*), valid $LIFETIME"
  # shellcheck disable=SC2086
  step ca certificate "$1" "$OUT/$name/tls.crt" "$OUT/$name/tls.key" \
    --ca-url "$CA_URL" --root "$ROOT" \
    --provisioner "$PROVISIONER" --provisioner-password-file "$PASSWORD_FILE" \
    --not-after "$LIFETIME" $san_args --force
  # step-ca returns the leaf followed by its intermediate: a complete chain for servers.
  # Readable by the unprivileged user inside the one container that mounts this directory
  # (Keycloak, nginx or Vault run as different uids). Nothing else mounts it.
  chmod 644 "$OUT/$name/tls.crt" "$OUT/$name/tls.key"
}

# --- workloads ---------------------------------------------------------------------------------
issue nginx    localhost 127.0.0.1 nginx
issue keycloak keycloak localhost 127.0.0.1
issue vault    vault localhost 127.0.0.1

# Backend services - mutual TLS between them, see docs/SMALLSTEP_PKI.md section 11.
# api-gateway's cert is also the source for its PKCS12 keystore (pki-keystore, below).
issue customer-service     customer-service     localhost 127.0.0.1
issue order-service        order-service        localhost 127.0.0.1
issue inventory-service    inventory-service    localhost 127.0.0.1
issue payment-service      payment-service      localhost 127.0.0.1
issue notification-service notification-service localhost 127.0.0.1
issue api-gateway          api-gateway          localhost 127.0.0.1

# Spring Cloud Gateway's outbound HTTP client needs a PKCS12 keystore, not the PEM pair
# every other consumer reads directly (see infrastructure/api-gateway's application-mtls.yml).
# Built here so it exists before api-gateway's first start; kept fresh afterwards by
# pki-renewer's own hourly rebuild loop (see renew.sh - that keystore doesn't hot-reload,
# so api-gateway needs a restart after each renewal regardless of how fresh this file is).
step certificate p12 "$OUT/api-gateway/keystore.p12" \
  "$OUT/api-gateway/tls.crt" "$OUT/api-gateway/tls.key" \
  --password-file /pki/pkcs12-password --force
chmod 644 "$OUT/api-gateway/keystore.p12"

log "done"
