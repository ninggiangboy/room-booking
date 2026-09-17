# The central stack, as Helm releases instead of compose.observability.yaml's plain containers --
# same components, same reasoning (room-booking-infra/docs/platform-architecture.md), different
# orchestrator. Values kept lean deliberately: this is sized for local kind verification on a
# developer laptop, not for the eventual real cluster -- revisit sizing when Phase 8 actually
# triggers against real infrastructure and real traffic.

resource "kubernetes_namespace_v1" "observability" {
  metadata {
    name = "observability"
  }
}

resource "helm_release" "kube_prometheus_stack" {
  name       = "kube-prometheus-stack"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  version    = "91.4.1"
  namespace  = kubernetes_namespace_v1.observability.metadata[0].name

  values = [yamlencode({
    grafana = {
      adminPassword = var.grafana_admin_password
      resources = {
        requests = { cpu = "50m", memory = "128Mi" }
      }
      # kube-prometheus-stack auto-provisions only its own Prometheus and Alertmanager as
      # datasources; Loki and Tempo need to be added explicitly, mirroring
      # observability/grafana/provisioning/datasources/datasources.yaml on the VPS path.
      additionalDataSources = [
        {
          name   = "Loki"
          type   = "loki"
          access = "proxy"
          url    = "http://loki.observability.svc.cluster.local:3100"
          jsonData = {
            derivedFields = [{
              datasourceUid = "tempo"
              matcherRegex  = "trace_id=(\\w+)"
              name          = "TraceID"
              url           = "$${__value.raw}"
            }]
          }
        },
        {
          name   = "Tempo"
          uid    = "tempo"
          type   = "tempo"
          access = "proxy"
          url    = "http://tempo.observability.svc.cluster.local:3200"
          jsonData = {
            tracesToLogsV2 = {
              datasourceUid  = "Loki"
              filterByTraceID = true
            }
          }
        },
      ]
    }
    prometheus = {
      prometheusSpec = {
        retention = "7d"
        resources = {
          requests = { cpu = "100m", memory = "256Mi" }
        }
      }
    }
    alertmanager = {
      alertmanagerSpec = {
        resources = {
          requests = { cpu = "25m", memory = "64Mi" }
        }
      }
    }
    prometheusOperator = {
      resources = {
        requests = { cpu = "50m", memory = "128Mi" }
      }
    }
  })]
}

resource "helm_release" "loki" {
  name       = "loki"
  repository = "https://grafana.github.io/helm-charts"
  chart      = "loki"
  version    = "7.3.0"
  namespace  = kubernetes_namespace_v1.observability.metadata[0].name

  values = [yamlencode({
    # Single-binary (monolithic) mode -- matches deploy/'s compose.deploy.yaml on the VPS path, per
    # docs/platform-architecture.md; not the simple-scalable/distributed modes this chart also
    # supports, which are unwarranted at this scale.
    deploymentMode = "SingleBinary"
    loki = {
      commonConfig = { replication_factor = 1 }
      storage      = { type = "filesystem" }
      schemaConfig = {
        configs = [{
          from         = "2024-01-01"
          store        = "tsdb"
          object_store = "filesystem"
          schema       = "v13"
          index        = { prefix = "index_", period = "24h" }
        }]
      }
      auth_enabled = false
    }
    singleBinary = {
      replicas = 1
      resources = {
        requests = { cpu = "50m", memory = "128Mi" }
      }
      persistence = { size = "5Gi" }
    }
    # This chart's own read/write/backend deployments are for simple-scalable/distributed mode;
    # SingleBinary mode doesn't use them, so scale them to zero rather than leave unused replicas.
    read      = { replicas = 0 }
    write     = { replicas = 0 }
    backend   = { replicas = 0 }
    gateway   = { enabled = false }
    test      = { enabled = false }
    monitoring = {
      selfMonitoring = { enabled = false, grafanaAgent = { installOperator = false } }
      lokiCanary     = { enabled = false }
    }
    # The chunks/results memcached caches are a performance optimization the chart enables by
    # default even in SingleBinary mode; disabled for a local kind cluster's resource budget
    # (confirmed empirically: chunksCache's pod couldn't schedule -- "Insufficient memory" -- on a
    # laptop-sized cluster already running kube-prometheus-stack and Tempo alongside it).
    chunksCache = { enabled = false }
    resultsCache = { enabled = false }
  })]
}

resource "helm_release" "tempo" {
  name       = "tempo"
  repository = "https://grafana.github.io/helm-charts"
  chart      = "tempo"
  version    = "1.24.4"
  namespace  = kubernetes_namespace_v1.observability.metadata[0].name

  values = [yamlencode({
    tempo = {
      retention = "168h" # 7d, leaner than the VPS path's 14d for a throwaway local cluster
    }
    resources = {
      requests = { cpu = "50m", memory = "128Mi" }
    }
    persistence = {
      enabled = true
      size     = "5Gi"
    }
  })]
}

resource "helm_release" "otel_collector" {
  name       = "otel-collector"
  repository = "https://open-telemetry.github.io/opentelemetry-helm-charts"
  chart      = "opentelemetry-collector"
  version    = "0.173.1"
  namespace  = kubernetes_namespace_v1.observability.metadata[0].name
  depends_on = [helm_release.loki, helm_release.tempo]

  values = [yamlencode({
    mode = "deployment"
    # The chart no longer defaults this -- confirmed empirically (a bare `mode: deployment` install
    # fails with "'image.repository' must be set"). Using the contrib distribution to match the
    # image otel-collector-config.yaml documents for the compose.observability.yaml path.
    image = {
      repository = "otel/opentelemetry-collector-contrib"
    }
    resources = {
      requests = { cpu = "50m", memory = "128Mi" }
    }
    config = {
      receivers = {
        otlp = {
          protocols = {
            http = { endpoint = "0.0.0.0:4318" }
            grpc = { endpoint = "0.0.0.0:4317" }
          }
        }
      }
      exporters = {
        "otlp_http/tempo" = {
          endpoint = "http://tempo.observability.svc.cluster.local:4318"
          tls      = { insecure = true }
        }
        "otlp_http/loki" = {
          endpoint = "http://loki.observability.svc.cluster.local:3100/otlp"
          tls      = { insecure = true }
        }
      }
      service = {
        pipelines = {
          traces = {
            receivers  = ["otlp"]
            processors = ["batch"]
            exporters  = ["otlp_http/tempo"]
          }
          logs = {
            receivers  = ["otlp"]
            processors = ["batch"]
            exporters  = ["otlp_http/loki"]
          }
        }
      }
    }
  })]
}
