# nginx-ingress's basic-auth annotation reads an htpasswd-format Secret; Terraform's built-in
# bcrypt() generates the hash so the plaintext password (var.ingest_auth_password, marked sensitive)
# never touches a file on disk. See k8s/observability/ingress.yaml for the Ingress resources that
# reference this Secret.

resource "kubernetes_secret_v1" "ingest_basic_auth" {
  metadata {
    name      = "ingest-basic-auth"
    namespace = kubernetes_namespace_v1.observability.metadata[0].name
  }

  data = {
    auth = "${var.ingest_auth_username}:${bcrypt(var.ingest_auth_password)}"
  }

  # bcrypt() re-salts on every evaluation, so without this the Secret's data would show as
  # "changed" on every single `terraform plan` even with no actual configuration change --
  # confirmed empirically on the first apply. Ignored after creation; change the password by
  # tainting this resource explicitly (`terraform apply -replace=...`), not by re-running apply.
  lifecycle {
    ignore_changes = [data]
  }
}
