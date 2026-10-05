#!/bin/sh
# Keeps every issued certificate fresh. Runs as the long-lived `pki-renewer` container: one
# `step ca renew --daemon` per certificate. The daemon renews at roughly two thirds of the
# certificate's lifetime, authenticating to the CA with the certificate it is about to replace
# (mTLS), so it needs no password and no provisioner. Files are rewritten in place; consumers pick the
# new certificate up on their own (Keycloak re-reads its PEM files hourly, nginx and Vault reload on a
# timer - see docker-compose.pki.yml).
set -eu

CA_URL="${CA_URL:-https://step-ca:9000}"
ROOT=/certs/ca/root_ca.crt

for name in nginx keycloak vault; do
  [ -s "/certs/$name/tls.crt" ] || continue
  echo "[pki-renewer] managing /certs/$name"
  step ca renew --daemon --force \
    --ca-url "$CA_URL" --root "$ROOT" \
    "/certs/$name/tls.crt" "/certs/$name/tls.key" &
done

wait
