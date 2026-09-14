# `analytics`

## Goal

Own the data platform — dataset and event contracts, arrivals, lineage, quality, semantic metrics,
and experimentation — as the module that observes the rest of the system and never repairs it.

## Forces that shaped it

- **Not merged with `ml`, despite one feature document covering both.** The dependency is real but
  strictly one-way: `ml → analytics`, 9 foreign keys, no edge back. A one-way dependency of that
  weight is an argument for two modules with a declared edge, not for one module of 40 types mixing
  two platforms. See [`ml.md`](ml.md).
- **It is the only module in the system with zero outbound cross-module foreign keys.** Nothing here
  constrains anything upstream, which is exactly what "analytics observes, never repairs" looks like
  when it is enforced by the schema rather than asserted in prose. That property is load-bearing and
  must survive: a new foreign key out of this module would silently invert the relationship.
- **Two roots, not one.** `data_product_registry` (in-module in-degree 6) anchors the lineage and
  quality side; `experiment_epochs` (6) anchors experimentation. They share a module because both
  are the measurement apparatus, but neither is subordinate to the other.
- **The epoch, not the experiment, is the experimentation root.** An experiment is frozen at first
  assignment, so `experiment_epochs` is what variants, metrics, assignments, and analysis runs all
  hang off — the definition is the container, the epoch is the thing with integrity.

## What it owns

- **`contract` cluster** — `data_product_registry` (root), `data_product_dependencies`,
  `data_corrections`, `privacy_subject_links`.
- **`arrival` cluster** — `event_definitions` (root), `event_definition_consumers`,
  `event_arrivals`.
- **`pipeline` cluster** — `pipeline_runs` (root), `pipeline_run_inputs`.
- **`quality` cluster** — `data_quality_check_definitions` (root), `data_quality_results`.
- **`metric` cluster** — `metric_definitions` (root), `metric_materializations`.
- **`experiment` cluster** — `experiment_definitions` and `experiment_epochs` (roots),
  `experiment_variants`, `experiment_metrics`, `experiment_exclusions`, `experiment_assignments`,
  `experiment_exposures`, `experiment_analysis_runs`, `experiment_analysis_estimates`,
  `experiment_actions`.

See [`../data-model/030-data-and-experimentation.md`](../data-model/030-data-and-experimentation.md)
and [`../features/data-experimentation-and-ml-platform.md`](../features/data-experimentation-and-ml-platform.md).

## Aggregate clusters inside it

23 tables across 6 clusters — the most clusters of any module, because this one genuinely holds six
separable concerns rather than one lifecycle with branches. `experiment` is by far the largest (10
tables) and is the only cluster with two roots.

## What it does not own

The event taxonomy's delivery machinery (`platform` owns `outbox_events` and
`consumer_inbox_receipts`; this module owns the *definition* of an event and the record of its
arrival, not its transport), the models that consume its features (`ml`), or any domain fact it
measures — by construction, since it holds no foreign key into one.

## Public API

No live service exists yet. `ml`, `hostops`, and `growth` are known consumers, so the eventual API
is a metric-definition and experiment-assignment lookup surface. Experiment assignment in particular
must be a published operation rather than a table other modules write to, since an epoch frozen at
first assignment cannot survive an outside writer.

## Allowed dependencies

None — with live code today, and none in the schema either. This is the only module for which that
is true.

## Data coupling `verify()` cannot see

Inbound: `ml` (9), `growth` (4), `hostops` (2). Outbound: none. The absence of outbound edges is the
notable fact; the inbound ones are invisible to `ApplicationModules.verify()` like every other
foreign key, per
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## What it leaves open

Whether `event_arrivals` should be fed from `platform`'s `outbox_events` relay or from an
independent ingestion path is left to this module's design. The two tables are adjacent in purpose
and must not be conflated — see
[`platform.md`](platform.md) and
[`../architecture/event-publication-registry.md`](../architecture/event-publication-registry.md) for
the three-way distinction between a registry row, an outbox row, and an event arrival.

## Exit criteria

- All 23 tables and their entities/repositories live under `dev.ngb.backend.analytics.internal.model`
  / `.repository`, in the six clusters above.
- `ApplicationModules.verify()` passes with `analytics` declaring no dependencies on any other
  business module.
