# Vault listener with TLS, using a certificate issued by the internal smallstep CA
# (docker-compose.pki.yml issues /certs/vault/tls.{crt,key}; pki-renewer keeps them fresh and the
# container sends Vault a SIGHUP every 6h so it re-reads them).

ui            = true
disable_mlock = true

storage "raft" {
  path    = "/vault/file"
  node_id = "vault-1"
}

listener "tcp" {
  address         = "0.0.0.0:8200"
  cluster_address = "0.0.0.0:8201"
  tls_cert_file   = "/certs/vault/tls.crt"
  tls_key_file    = "/certs/vault/tls.key"
  tls_min_version = "tls12"
}

api_addr     = "https://vault:8200"
cluster_addr = "https://vault:8201"

telemetry {
  disable_hostname          = true
  prometheus_retention_time = "24h"
}
