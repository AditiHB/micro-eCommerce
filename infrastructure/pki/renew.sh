#!/bin/sh
# Keeps every issued certificate fresh. Runs as the long-lived `pki-renewer` container: one
# `step ca renew --daemon` per certificate. The daemon renews at roughly two thirds of the
# certificate's lifetime, authenticating to the CA with the certificate it is about to replace
# (mTLS), so it needs no password and no provisioner. Files are rewritten in place; consumers pick the
# new certificate up on their own (Keycloak re-reads its PEM files hourly, nginx and Vault reload on a
# timer - see docker-compose.pki.yml). Backend services' Spring Boot PEM SSL bundles
# (reload-on-update: true) pick up a renewed cert the same way, no restart needed.
set -eu

CA_URL="${CA_URL:-https://step-ca:9000}"
ROOT=/certs/ca/root_ca.crt

for name in nginx keycloak vault customer-service order-service inventory-service \
            payment-service notification-service api-gateway; do
  [ -s "/certs/$name/tls.crt" ] || continue
  echo "[pki-renewer] managing /certs/$name"
  step ca renew --daemon --force \
    --ca-url "$CA_URL" --root "$ROOT" \
    "/certs/$name/tls.crt" "/certs/$name/tls.key" &
done

# api-gateway is the one exception: Spring Cloud Gateway's outbound HTTP client needs a PKCS12
# keystore, not the PEM pair every other consumer reads directly - and that keystore does not
# hot-reload, so api-gateway needs a restart after each renewal anyway (see its own
# application-mtls.yml). Decoupled from the daemon's own renewal timing above: this just rebuilds
# the keystore from whatever's currently on disk once an hour, which converges within an hour of
# any actual renewal without needing to hook the daemon's internal schedule.
if [ -s /certs/api-gateway/tls.crt ]; then
  (
    while true; do
      step certificate p12 /certs/api-gateway/keystore.p12 \
        /certs/api-gateway/tls.crt /certs/api-gateway/tls.key \
        --password-file /pki/pkcs12-password --force || true
      sleep 3600
    done
  ) &
fi

wait
