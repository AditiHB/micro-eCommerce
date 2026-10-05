#!/bin/bash
# Development-only seed for the local Keycloak: a confidential client that may use the password grant
# (so scripts and the Karate suite can obtain tokens) and a few test users, one per role.
#
# This is deliberately NOT part of realm-ecommerce.json: the realm file describes what production has
# (roles, public client, machine identities); this file adds throwaway credentials that must never
# exist outside a developer machine. Every secret comes from the environment (generated into .env by
# scripts/gen-env.sh), nothing is committed.
#
# Idempotent: safe to re-run.
set -euo pipefail

# kcadm keeps its login token in a config file; the image user's home may be read-only.
kc() { /opt/keycloak/bin/kcadm.sh "$@" --config /tmp/kcadm.config; }
SERVER="${KEYCLOAK_URL:-http://keycloak:8080}"
REALM=ecommerce

: "${KC_BOOTSTRAP_ADMIN_USERNAME:?}" "${KC_BOOTSTRAP_ADMIN_PASSWORD:?}"
: "${E2E_CLIENT_SECRET:?}" "${E2E_ADMIN_PASSWORD:?}" "${E2E_MANAGER_PASSWORD:?}" "${E2E_USER_PASSWORD:?}"

# Trust the platform CA when Keycloak is served over TLS (docker-compose.pki.yml).
if [ -n "${KEYCLOAK_TRUSTSTORE:-}" ]; then
  kc config truststore --trustpass changeit "$KEYCLOAK_TRUSTSTORE"
fi

kc config credentials --server "$SERVER" --realm master \
  --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD"

# --- e2e client --------------------------------------------------------------------------------
if [ -z "$(kc get clients -r $REALM -q clientId=ecommerce-e2e --fields id --format csv --noquotes)" ]; then
  echo "Creating client ecommerce-e2e"
  cat > /tmp/e2e-client.json <<EOF
{
  "clientId": "ecommerce-e2e",
  "name": "E2E test automation (development only)",
  "enabled": true,
  "protocol": "openid-connect",
  "publicClient": false,
  "secret": "${E2E_CLIENT_SECRET}",
  "standardFlowEnabled": false,
  "directAccessGrantsEnabled": true,
  "serviceAccountsEnabled": false,
  "defaultClientScopes": ["profile", "email", "roles"],
  "protocolMappers": [
    { "name": "ecommerce-api-audience", "protocol": "openid-connect", "protocolMapper": "oidc-audience-mapper",
      "consentRequired": false,
      "config": { "included.custom.audience": "ecommerce-api", "id.token.claim": "false", "access.token.claim": "true" } },
    { "name": "customer-id", "protocol": "openid-connect", "protocolMapper": "oidc-usermodel-attribute-mapper",
      "consentRequired": false,
      "config": { "user.attribute": "customer_id", "claim.name": "customer_id", "jsonType.label": "String",
                  "id.token.claim": "false", "access.token.claim": "true", "userinfo.token.claim": "false" } }
  ]
}
EOF
  kc create clients -r $REALM -f /tmp/e2e-client.json
  rm -f /tmp/e2e-client.json
else
  echo "Client ecommerce-e2e already exists"
fi

# --- users -------------------------------------------------------------------------------------
create_user() { # username role password [customer_id]
  local username="$1" role="$2" password="$3" customer_id="${4:-}"
  if [ -z "$(kc get users -r $REALM -q username="$username" --fields id --format csv --noquotes)" ]; then
    echo "Creating user $username ($role)"
    if [ -n "$customer_id" ]; then
      kc create users -r $REALM -s username="$username" -s enabled=true -s email="$username@example.test" \
        -s emailVerified=true -s firstName=Dev -s lastName="$role" -s "attributes.customer_id=$customer_id"
    else
      kc create users -r $REALM -s username="$username" -s enabled=true -s email="$username@example.test" \
        -s emailVerified=true -s firstName=Dev -s lastName="$role"
    fi
    kc add-roles -r $REALM --uusername "$username" --rolename "$role"
  else
    echo "User $username already exists"
  fi
  # Always (re)apply the password so rotating it in .env takes effect.
  kc set-password -r $REALM --username "$username" --new-password "$password"
}

create_user karate_admin ADMIN "$E2E_ADMIN_PASSWORD"
create_user karate_manager MANAGER "$E2E_MANAGER_PASSWORD"
create_user karate_user USER "$E2E_USER_PASSWORD" 1

echo "Development identities ready."
