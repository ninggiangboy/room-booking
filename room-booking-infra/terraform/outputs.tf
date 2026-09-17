output "kubeconfig_path" {
  description = "Written by the kind provider; `export KUBECONFIG=$(terraform output -raw kubeconfig_path)` or use kind's own `kind export kubeconfig --name <cluster_name>`."
  value       = try(kind_cluster.this[0].kubeconfig_path, null)
}
