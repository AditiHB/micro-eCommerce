# Operator policy. bootstrap.sh issues one token with this policy and then REVOKES the root token:
# day-to-day administration never uses root. Deliberately cannot seal, step down or change the
# audit configuration; those need a fresh root token generated with the unseal key (break glass).

path "secret/*"            { capabilities = ["create", "read", "update", "patch", "delete", "list"] }
path "database/*"          { capabilities = ["create", "read", "update", "delete", "list"] }
path "auth/approle/*"      { capabilities = ["create", "read", "update", "delete", "list"] }
path "auth/kubernetes/*"   { capabilities = ["create", "read", "update", "delete", "list"] }
path "auth/token/create"   { capabilities = ["create", "update"] }
path "auth/token/lookup*"  { capabilities = ["read", "update"] }
path "auth/token/revoke*"  { capabilities = ["update"] }
path "pki*"                { capabilities = ["create", "read", "update", "delete", "list"] }
path "sys/policies/acl/*"  { capabilities = ["create", "read", "update", "delete", "list"] }
path "sys/policies/acl"    { capabilities = ["list"] }
path "sys/auth"            { capabilities = ["read", "list"] }
path "sys/mounts"          { capabilities = ["read", "list"] }
path "sys/mounts/*"        { capabilities = ["create", "read", "update", "list"] }
path "sys/leases/*"        { capabilities = ["create", "read", "update", "list"] }
path "sys/health"          { capabilities = ["read"] }
path "sys/seal-status"     { capabilities = ["read"] }
