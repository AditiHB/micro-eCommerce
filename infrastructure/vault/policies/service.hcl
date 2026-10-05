# Template: bootstrap.sh substitutes __SERVICE__ with each service name (customer-service, ...).
# A service can read ITS OWN secrets and mint credentials for ITS OWN database - nothing else.

# Platform-wide values (for example the Redis password) and this service's own secrets.
path "secret/data/application"   { capabilities = ["read"] }
path "secret/data/application/*" { capabilities = ["read"] }
path "secret/data/__SERVICE__"   { capabilities = ["read"] }
path "secret/data/__SERVICE__/*" { capabilities = ["read"] }

# Short-lived database credentials for this service's own database role only.
path "database/creds/__SERVICE__" { capabilities = ["read"] }

# Lease and token self-management: the client renews and revokes what it was issued.
path "auth/token/renew-self"  { capabilities = ["update"] }
path "auth/token/lookup-self" { capabilities = ["read"] }
path "sys/leases/renew"       { capabilities = ["update"] }
path "sys/leases/revoke"      { capabilities = ["update"] }
path "sys/capabilities-self"  { capabilities = ["update"] }
