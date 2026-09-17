# TLS termination and (for the two ingestion hostnames) basic auth in front of every Service in
# the observability namespace -- see room-booking-infra/docs/observability.md and
# k8s/observability/ingress.yaml for the actual Ingress resources.

resource "helm_release" "ingress_nginx" {
  name             = "ingress-nginx"
  repository       = "https://kubernetes.github.io/ingress-nginx"
  chart            = "ingress-nginx"
  version          = "4.15.1"
  namespace        = "ingress-nginx"
  create_namespace = true

  values = [yamlencode({
    controller = {
      # kind maps the host's 80/443 to the control-plane node's hostPort (cluster.tf); the
      # controller must actually bind those ports on that node.
      hostPort = { enabled = true }
      service  = { type = "ClusterIP" }
      resources = {
        requests = { cpu = "50m", memory = "90Mi" }
      }
    }
  })]
}

# Self-signed locally -- a throwaway kind cluster has no public DNS for ACME to validate against.
# Swapping to Let's Encrypt in a real cluster is changing this ClusterIssuer's spec (acme instead of
# selfSigned) and var.domain to a real one; nothing else in this file or the Ingress resources
# changes.
resource "helm_release" "cert_manager" {
  name             = "cert-manager"
  repository       = "https://charts.jetstack.io"
  chart            = "cert-manager"
  version          = "v1.21.2"
  namespace        = "cert-manager"
  create_namespace = true

  set = [{
    name  = "crds.enabled"
    value = "true"
  }]
}

resource "kubernetes_manifest" "self_signed_issuer" {
  depends_on = [helm_release.cert_manager]

  manifest = {
    apiVersion = "cert-manager.io/v1"
    kind       = "ClusterIssuer"
    metadata = {
      name = "self-signed"
    }
    spec = {
      selfSigned = {}
    }
  }
}

# The two public ingestion hostnames (docs/observability.md), as Kubernetes Ingress resources --
# same shape as caddy/Caddyfile on the VPS path, different mechanism. var.domain is a placeholder
# for local kind verification; a real domain plus switching this ClusterIssuer to `acme` is what
# Phase 8's migration guide (docs/migration-vps-to-kubernetes.md) actually needs when it runs for
# real.
resource "kubernetes_ingress_v1" "otlp" {
  depends_on = [helm_release.otel_collector]

  metadata {
    name      = "otlp"
    namespace = kubernetes_namespace_v1.observability.metadata[0].name
    annotations = {
      "cert-manager.io/cluster-issuer"               = kubernetes_manifest.self_signed_issuer.manifest.metadata.name
      "nginx.ingress.kubernetes.io/auth-type"         = "basic"
      "nginx.ingress.kubernetes.io/auth-secret"       = kubernetes_secret_v1.ingest_basic_auth.metadata[0].name
      "nginx.ingress.kubernetes.io/auth-secret-type"  = "auth-file"
    }
  }
  spec {
    ingress_class_name = "nginx"
    tls {
      hosts       = ["otel.${var.domain}"]
      secret_name = "otlp-tls"
    }
    rule {
      host = "otel.${var.domain}"
      http {
        path {
          path      = "/"
          path_type = "Prefix"
          backend {
            service {
              name = "otel-collector-opentelemetry-collector"
              port { number = 4318 }
            }
          }
        }
      }
    }
  }
}

resource "kubernetes_ingress_v1" "loki_push" {
  depends_on = [helm_release.loki]

  metadata {
    name      = "loki-push"
    namespace = kubernetes_namespace_v1.observability.metadata[0].name
    annotations = {
      "cert-manager.io/cluster-issuer"              = kubernetes_manifest.self_signed_issuer.manifest.metadata.name
      "nginx.ingress.kubernetes.io/auth-type"        = "basic"
      "nginx.ingress.kubernetes.io/auth-secret"      = kubernetes_secret_v1.ingest_basic_auth.metadata[0].name
      "nginx.ingress.kubernetes.io/auth-secret-type" = "auth-file"
    }
  }
  spec {
    ingress_class_name = "nginx"
    tls {
      hosts       = ["loki.${var.domain}"]
      secret_name = "loki-tls"
    }
    rule {
      host = "loki.${var.domain}"
      http {
        # Push path only -- Loki's query API stays off the public internet; only Grafana (internal
        # Docker/Kubernetes network) queries it. Mirrors caddy/Caddyfile's scoping on the VPS path.
        path {
          path      = "/loki/api/v1/push"
          path_type = "Exact"
          backend {
            service {
              name = "loki"
              port { number = 3100 }
            }
          }
        }
      }
    }
  }
}
