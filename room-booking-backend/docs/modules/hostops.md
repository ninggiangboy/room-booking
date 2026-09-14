# `hostops`

## Goal

Own the host-facing operational surface — performance metric, benchmark, demand forecast, priced
advice, listing-quality checklist, and bulk edit — as the module that tells a host what is happening
and what it recommends, while owning none of the facts it reports on.

## Forces that shaped it

- **It is the most broadly dependent module in the system**: outbound foreign keys into nine other
  modules (`identity`, `supply`, `market`, `ml`, `analytics`, `pricing`, `inventory`, `review`,
  `booking`) and not one edge inbound. That is the correct shape for a reporting and advice layer,
  and it is also why this module must be extracted last among its peers — every one of its
  dependencies must already exist.
- **No single dominant root.** The highest in-module in-degree is 2 (`demand_forecast_runs`,
  `host_metric_publications`), against 7 for `operational_stays` in `stay` or 26 for `support_cases`
  in `support`. This module is genuinely six small independent concerns, not one lifecycle, and the
  clustering reflects that rather than inventing a spine that is not there.
- **Advice carries its evidence, its uncertainty, and its cost** — that is what migration `033`
  built. `host_advice_disclosures` and `host_advice_decisions` are separate rows from the forecast
  and the suggestion that produced them, because a recommendation a host acted on must remain
  explainable after the model that made it is retired.
- **Kept separate from `hostverification`.** Both are host-facing and neither depends on the other.
  One is seller compliance at onboarding, the other is operational performance during trading; they
  share an audience, not a lifecycle.

## What it owns

- **`metric` cluster** — `host_metric_publications` (root), `host_performance_metrics`,
  `host_response_metrics`.
- **`benchmark` cluster** — `benchmark_cohort_definitions` (root), `market_benchmark_aggregates`.
- **`forecast` cluster** — `demand_forecast_runs` (root), `demand_forecasts`,
  `promotion_suggestions`, `calendar_value_sources`.
- **`advice` cluster** — `host_advice_disclosures` (root), `host_advice_decisions`,
  `host_payout_previews`.
- **`checklist` cluster** — `listing_quality_checklist_versions` (root),
  `listing_quality_checklist_items`, `listing_quality_checklist_states`.
- **`bulkedit` cluster** — `host_bulk_edit_requests` (root), `host_bulk_edit_targets`.

See [`../data-model/033-host-operations.md`](../data-model/033-host-operations.md). This module has
**no feature design document of its own** — a gap worth closing before it gets a service layer.

## Aggregate clusters inside it

17 tables across 6 clusters, none larger than four tables. `promotion_suggestions` sits in `forecast`
rather than in a pricing-shaped cluster because it hangs off `demand_forecast_runs`: the suggestion
is an output of a forecast run, and the promotion it suggests belongs to `pricing`.

## What it does not own

Any fact it reports. The metric is computed from `booking`, `review`, `inventory`, and `pricing`
data; the promotion it suggests is created in `pricing`; the payout it previews is decided in
`ledger`. `host_payout_previews` in particular is a projection and must never be treated as an
instruction — the authoritative payout lives in [`ledger.md`](ledger.md).

## Public API

No live service exists yet, and nothing depends on this module. The eventual API is a host-facing
read surface plus the bulk-edit command; bulk edit is the one place this module writes outward, and
it must do so by calling the owning module's API rather than by writing to `supply` or `inventory`
tables directly.

## Allowed dependencies

None with live code today. Schema carries 6 foreign keys into `identity`, 6 into `supply`, 4 into
`market`, 3 into `ml`, 2 each into `analytics`, `pricing`, and `inventory`, 1 each into `review` and
`booking`.

## Data coupling `verify()` cannot see

Inbound: none. Outbound: nine modules, 27 foreign keys, the widest fan-out in the system. A module
with no inbound edges and this much outbound reach is the easiest place for a boundary to erode
unnoticed, because nothing downstream breaks when it does — and none of it is visible to
`ApplicationModules.verify()`; see
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## What it leaves open

Whether a bulk edit should execute synchronously against each target module or be dispatched as
per-target events is left to this module's design. `host_bulk_edit_targets` already records a
per-target result, which is the shape a registry-backed listener would need — see
[`../architecture/event-publication-registry.md`](../architecture/event-publication-registry.md).

## Exit criteria

- All 17 tables and their entities/repositories live under `dev.ngb.backend.hostops.internal.model`
  / `.repository`, in the six clusters above.
- `ApplicationModules.verify()` passes with `hostops` declaring dependencies on exactly the nine
  modules listed above and nothing else.
