#!/bin/bash
# Prepares the git-ignored inputs Kustomize needs to build the cluster's Secret and Keycloak ConfigMaps:
#
#   k8s/base/generated/secrets.env            the Secret's keys, taken from .env (scripts/gen-env.sh)
#   k8s/base/generated/realm-ecommerce.json   a copy of the realm definition
#   k8s/base/generated/seed-dev.sh            a copy of the development-user seed script
#
#   k8s/overlays/postgres/generated/init-multiple-postgres-databases.sh   the shared Postgres init script
#
# Why a copy: `kubectl apply -k` refuses to read files outside the kustomization directory, and the
# canonical realm / seed files live under infrastructure/keycloak/ (shared with Docker Compose).
# Nothing here is committed - the Secret is created from your own generated values, never from the
# repository. In a real cluster replace this with External Secrets or Sealed Secrets.
set -euo pipefail

cd "$(dirname "$0")/.."

[ -f .env ] || { echo "No .env found - run scripts/gen-env.sh first." >&2; exit 1; }
set -a; . ./.env; set +a

OUT=k8s/base/generated
mkdir -p "$OUT"
umask 077

cat > "$OUT/secrets.env" <<EOF
eureka-password=${EUREKA_PASSWORD:?}
config-server-password=${CONFIG_SERVER_PASSWORD:?}
redis-password=${REDIS_PASSWORD:?}
notification-client-secret=${NOTIFICATION_CLIENT_SECRET:?}
kc-bootstrap-admin-username=${KC_BOOTSTRAP_ADMIN_USERNAME:?}
kc-bootstrap-admin-password=${KC_BOOTSTRAP_ADMIN_PASSWORD:?}
e2e-client-secret=${E2E_CLIENT_SECRET:?}
e2e-admin-password=${E2E_ADMIN_PASSWORD:?}
e2e-manager-password=${E2E_MANAGER_PASSWORD:?}
e2e-user-password=${E2E_USER_PASSWORD:?}
postgres-admin-password=${POSTGRES_ADMIN_PASSWORD:?}
vault-db-admin-password=${VAULT_DB_ADMIN_PASSWORD:?}
customer-db-password=${CUSTOMER_DB_PASSWORD:?}
order-db-password=${ORDER_DB_PASSWORD:?}
inventory-db-password=${INVENTORY_DB_PASSWORD:?}
payment-db-password=${PAYMENT_DB_PASSWORD:?}
notification-db-password=${NOTIFICATION_DB_PASSWORD:?}
product-db-password=${PRODUCT_DB_PASSWORD:?}
EOF

cp infrastructure/keycloak/realm-ecommerce.json "$OUT/realm-ecommerce.json"
cp infrastructure/keycloak/seed-dev.sh "$OUT/seed-dev.sh"

# The Postgres overlay builds its init ConfigMap from the same script Docker Compose uses.
PG_OUT=k8s/overlays/postgres/generated
mkdir -p "$PG_OUT"
cp infrastructure/postgres/init-multiple-postgres-databases.sh "$PG_OUT/init-multiple-postgres-databases.sh"

echo "Wrote $OUT/ (git-ignored). Deploy with:  kubectl apply -k k8s/overlays/postgres"
