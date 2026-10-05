# Vault server configuration for the local stack.
#
# Storage is Vault's integrated Raft store (one node here; three or five in a real cluster), so no
# external database is needed. The listener is plain HTTP on the internal compose network only; the
# smallstep overlay (docker-compose.pki.yml) swaps in vault-tls.hcl, which serves TLS with a
# certificate issued by the internal CA.

ui = true

# Containers share the host kernel's memory policy; locking pages needs IPC_LOCK and is meaningless
# on Docker Desktop. Real hosts should leave mlock enabled and disable swap.
disable_mlock = true

storage "raft" {
  path    = "/vault/file"
  node_id = "vault-1"
}

listener "tcp" {
  address         = "0.0.0.0:8200"
  cluster_address = "0.0.0.0:8201"
  tls_disable     = true
}

api_addr     = "http://vault:8200"
cluster_addr = "http://vault:8201"

telemetry {
  disable_hostname          = true
  prometheus_retention_time = "24h"
}
