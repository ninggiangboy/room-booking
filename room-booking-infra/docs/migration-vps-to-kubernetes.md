> Written as design, per `docs/platform-architecture.md` Phase 8, and not to be executed against a
> real deployed environment until its trigger fires: more than one application instance needed, more
> than one host, or a failure domain a single VPS cannot survive. **The mechanics below, however,
> have been verified end to end against a local `kind` cluster** — see "Local verification, already
> done" — so this is closer to "ready, waiting for the trigger" than a pure design document.

# Migration guide — VPS to Kubernetes

## Local verification, already done

`terraform apply` in `room-booking-infra/terraform/` was run for real against a local `kind`
cluster: the cluster, `ingress-nginx`, `cert-manager` with a self-signed `ClusterIssuer`,
`kube-prometheus-stack`, Loki (`SingleBinary` mode), Tempo (monolithic), the OpenTelemetry Collector,
and two Kubernetes Ingress resources (`otlp`, `loki-push`) all came up healthy. Confirmed with real
traffic, not just "pods are Running":

- The application (`./gradlew bootRun` locally, `OTLP_ENDPOINT` pointed at the collector) pushed
  traces and logs that arrived in Tempo and Loki, correctly labelled `deployment_environment`.
- Grafana's `additionalDataSources` wiring (Loki + Tempo, added to `kube-prometheus-stack`'s values
  since that chart only auto-provisions its own Prometheus/Alertmanager) produced all four
  datasources, matching the VPS path's hand-provisioned `datasources.yaml`.
- The Ingress path was tested directly, not assumed: a request with no credentials got `401`; the
  same request with the right basic-auth credentials got through to the collector; Loki's Ingress
  correctly serves only `/loki/api/v1/push` (`404` on the query API path), matching
  `caddy/Caddyfile`'s scoping on the VPS path.

**Corrections this pass made to the original design**, kept here so they aren't silently lost:

- `kubernetes_manifest` (used for the `ClusterIssuer`) cannot be planned in the same `terraform
  apply` as the cluster it depends on — the provider needs to query the live API server's CRD schema
  at plan time. First apply is `-target=kind_cluster.this -target=helm_release.cert_manager`, then a
  normal `terraform apply` for everything else. This is a real property of the provider, not
  specific to this cluster, and applies the same way against a real cloud cluster later.
- Terraform's `bcrypt()` re-salts on every evaluation; without `lifecycle { ignore_changes = [data]
  }` on the Secret it produces, every subsequent `terraform plan` shows the Secret as changed with no
  actual configuration change. Fixed in `terraform/secrets.tf`.
- The `opentelemetry-collector` Helm chart (0.173.1) requires `image.repository` set explicitly —
  it has no default. Set to `otel/opentelemetry-collector-contrib`, matching the image
  `otel-collector-config.yaml` documents for the VPS path.
- The `loki` chart's memcached-based `chunksCache`/`resultsCache` are enabled by default even in
  `SingleBinary` mode and don't fit a laptop-sized cluster's resource budget alongside
  `kube-prometheus-stack` and Tempo — disabled explicitly in `terraform/observability.tf`.
- **Running the full stack alongside normal development work exhausted this machine's Docker
  resources and crashed the Docker daemon** (OrbStack) partway through verification; recovered with
  `orbctl start` and the `kind` cluster's containers survived. This is the same under-sizing risk
  `docs/observability.md` already calls out for a VPS, just encountered on the verification machine
  instead — a real cluster's node sizing needs to account for kube-prometheus-stack + Loki + Tempo +
  the OTel Collector + ingress-nginx + cert-manager running concurrently, not just the application.

The cluster was torn down (`terraform destroy`) after verification, per its own design as a
throwaway validation cluster — nothing here is a running environment.

Environments migrate **one at a time, `dev` first** — the same reason they were built in that order:
`dev` is the cheapest place to find out the mapping below is wrong.

## Component mapping

| Compose service | Kubernetes equivalent |
| --- | --- |
| `room-booking-backend` (application) | `Deployment` + `Service`, manifest under `k8s/`, following `docs/service-contract.md`'s template |
| `postgres` | `StatefulSet` with a `PersistentVolumeClaim`, in-cluster — a placeholder per `docs/platform-architecture.md`, to be replaced by a managed database once a cloud provider is chosen |
| `minio` | `StatefulSet` with a `PersistentVolumeClaim`, in-cluster — same placeholder status as `postgres` |
| `caddy` (per application host) | `nginx-ingress` + `cert-manager`, terminating TLS and enforcing basic auth via Ingress annotations |
| `prometheus` (central) | `kube-prometheus-stack` Helm release |
| `loki` (central) | Loki Helm release, single-binary mode (matching the VPS's monolithic deployment) |
| `tempo` (central) | Tempo Helm release, monolithic mode |
| `grafana` (central) | The Grafana bundled with `kube-prometheus-stack`, rather than a separate release |
| `otel-collector` (central) | OpenTelemetry Collector Helm release |
| `caddy` (central) | `nginx-ingress` + `cert-manager`, same as above |

Provisioning itself — the cluster and every Helm release on it — is Terraform
(`room-booking-infra/terraform/`), not hand-run `kind create cluster` / `helm install`: `cluster.tf`
sits behind `var.cluster_provisioner` so a managed-Kubernetes module replaces the local `kind`
cluster used for verification without touching anything above it.

## Data inventory

**Must migrate:**
- Postgres — `pg_dump` from the VPS, restore into the new `StatefulSet`'s volume, verified with a
  row-count or checksum comparison before cutover.
- MinIO objects — `mc mirror` from the VPS bucket to the new in-cluster instance.

**Must not migrate — stated explicitly so nobody builds a path nobody needs:**
- Prometheus, Loki, and Tempo data. This is short-retention operational telemetry, not a system of
  record (see `docs/observability.md`). The new cluster starts with empty telemetry history; this is
  expected and acceptable.

## Configuration delta

Everything that changes is an environment variable, per the portability mechanism in
`docs/platform-architecture.md` — never application code:

- `OTLP_ENDPOINT` and the Loki-push endpoint move from the VPS observability host's hostnames to the
  Kubernetes Ingress hostnames (which may be the same hostnames, re-pointed in DNS, or new ones —
  decide and record which the first time this runs).
- `OTLP_AUTH_HEADER` and every other basic-auth credential are re-provisioned as Kubernetes Secrets
  rather than read from `.env` — same values, different storage, per
  `docs/configuration-and-secrets.md`.
- `DEPLOY_ENV` is unchanged — the environment's identity doesn't change, only its runtime target.

## Cutover

1. Deploy the environment being migrated (start with `dev`) fully into Kubernetes, verified
   end-to-end against the checklist in that environment's runbook, **while the VPS deployment keeps
   running** — this is not a switch, it's standing up a second, complete instance.
2. Migrate Postgres and MinIO data (above) into the Kubernetes deployment.
3. Point DNS at the Kubernetes Ingress.
4. Keep the VPS deployment running, untouched, for a rollback window — how long is a judgement call
   recorded in that environment's runbook the first time this happens; DNS TTLs and how quickly a
   problem would surface are the relevant inputs.
5. Decommission the VPS deployment only after that window passes with no rollback needed.

Repeat for `staging`, then `production`, each only after the previous environment's migration is
confirmed healthy — the same staged-confidence reasoning that put `dev` before `staging` before
`production` in the first place.
