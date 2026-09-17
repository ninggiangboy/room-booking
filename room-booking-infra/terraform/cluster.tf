# kind for local verification, per var.cluster_provisioner. A real cloud provider becomes a
# sibling file (e.g. cluster_eks.tf) selected by the same variable when Phase 8 actually triggers
# -- see room-booking-infra/docs/platform-architecture.md and variables.tf.

resource "kind_cluster" "this" {
  count           = var.cluster_provisioner == "kind" ? 1 : 0
  name            = var.cluster_name
  wait_for_ready  = true

  kind_config {
    kind        = "Cluster"
    api_version = "kind.x-k8s.io/v1alpha4"

    node {
      role = "control-plane"

      # Publishes the Ingress controller's ports to the host so http(s)://localhost reaches
      # services inside the cluster without a cloud load balancer, which a local kind cluster has
      # no equivalent of.
      extra_port_mappings {
        container_port = 80
        host_port       = 8080
      }
      extra_port_mappings {
        container_port = 443
        host_port       = 8443
      }
    }
  }
}
