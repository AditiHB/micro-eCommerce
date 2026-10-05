# The API gateway has no database; it only needs the platform-wide secrets and its own.

path "secret/data/application"   { capabilities = ["read"] }
path "secret/data/application/*" { capabilities = ["read"] }
path "secret/data/api-gateway"   { capabilities = ["read"] }
path "secret/data/api-gateway/*" { capabilities = ["read"] }

path "auth/token/renew-self"  { capabilities = ["update"] }
path "auth/token/lookup-self" { capabilities = ["read"] }
path "sys/leases/renew"       { capabilities = ["update"] }
path "sys/leases/revoke"      { capabilities = ["update"] }
path "sys/capabilities-self"  { capabilities = ["update"] }
