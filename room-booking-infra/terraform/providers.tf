provider "kind" {}

# Both configured from the kind cluster's own kubeconfig output, so `terraform apply` creates the
# cluster and deploys into it in one pass -- no separate `kind export kubeconfig` step.
provider "kubernetes" {
  host                   = kind_cluster.this[0].endpoint
  cluster_ca_certificate = kind_cluster.this[0].cluster_ca_certificate
  client_certificate     = kind_cluster.this[0].client_certificate
  client_key             = kind_cluster.this[0].client_key
}

provider "helm" {
  kubernetes = {
    host                   = kind_cluster.this[0].endpoint
    cluster_ca_certificate = kind_cluster.this[0].cluster_ca_certificate
    client_certificate     = kind_cluster.this[0].client_certificate
    client_key             = kind_cluster.this[0].client_key
  }
}
