# Behind var.cluster_provisioner so a managed-Kubernetes module can replace the local kind cluster
# without touching anything below it -- see room-booking-infra/docs/platform-architecture.md.
variable "cluster_provisioner" {
  description = "Which cluster module provisions Kubernetes. Only \"kind\" exists so far (local verification); a real cloud provider is added as a new module when Phase 8 actually triggers, not designed speculatively ahead of a chosen provider."
  type        = string
  default     = "kind"

  validation {
    condition     = contains(["kind"], var.cluster_provisioner)
    error_message = "Only \"kind\" is implemented. See docs/platform-architecture.md Phase 8."
  }
}

variable "cluster_name" {
  description = "kind cluster name."
  type        = string
  default     = "room-booking"
}

# --- Ingress / TLS ---

variable "domain" {
  description = "Base domain for the observability stack's Ingress hostnames. Left as a placeholder for local kind verification (no public DNS exists for a throwaway cluster); a real domain is required before cert-manager's ClusterIssuer can be switched from self-signed to ACME/Let's Encrypt."
  type        = string
  default     = "observability.kind.local"
}

# --- Basic-auth credentials for the ingestion/scrape paths (docs/observability.md) ---
# Terraform variables, not files, because these must be sensitive -- never written to a committed
# values file. Pass via TF_VAR_ingest_auth_password or a local .tfvars that's gitignored.

variable "ingest_auth_username" {
  description = "Username Caddy/Ingress checks for OTLP push and Loki push (mirrors observability/.env.example's INGEST_AUTH_USERNAME for the VPS path)."
  type        = string
  default     = "otel-ingest"
}

variable "ingest_auth_password" {
  description = "Plaintext password for the above; Terraform hashes it before it reaches any Secret or ConfigMap."
  type        = string
  sensitive   = true
}

variable "grafana_admin_password" {
  description = "Grafana's own admin login -- not basic auth."
  type        = string
  sensitive   = true
}
