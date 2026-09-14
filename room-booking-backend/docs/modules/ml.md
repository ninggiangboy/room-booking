# `ml`

## Goal

Own the machine-learning platform — feature store, label, model registry, and prediction — as the
module that guarantees a model advises inside deterministic constraints and never decides.

## Forces that shaped it

- **Kept separate from `analytics`, despite sharing one feature document.** The dependency is
  one-way and dense (`ml → analytics`, 9 foreign keys, nothing back), which is the shape that argues
  for two modules and a declared edge rather than one merged module. Merging would produce 40 types
  spanning the measurement platform and the decisioning platform, and would hide the direction that
  matters: features are built *from* analytics, never the reverse.
- **`model_versions` is the root** (in-module in-degree 5), and it references the feature set, label
  definition, and training manifest it was built from. A registered version is frozen, so the
  reference runs from the model to its inputs — that is what makes point-in-time correctness
  checkable after the fact.
- **`feature_definitions` and `feature_set_versions` are co-roots of the feature side** (in-degree 4
  each). They stay in one cluster because a set version is meaningless without its members, and
  offline and online value tables hang off the definition rather than the set.
- **`prediction` is its own cluster, not part of `model`.** `decision_references` exists to record
  that a prediction informed a decision taken elsewhere. Keeping it apart from the registry is what
  keeps "advises" and "decides" legible as two different things.

## What it owns

- **`feature` cluster** — `feature_definitions` and `feature_set_versions` (roots),
  `feature_set_members`, `feature_invalidations`, `offline_feature_values`, `online_feature_values`.
- **`label` cluster** — `label_definitions` (root), `label_observations`.
- **`model` cluster** — `model_versions` (root), `model_approvals`, `model_evaluation_runs`,
  `model_evaluation_slices`, `model_release_routes`, `model_actions`,
  `training_dataset_manifests`.
- **`prediction` cluster** — `prediction_records` (root), `decision_references`.

See [`../data-model/031-ml-platform.md`](../data-model/031-ml-platform.md) and
[`../features/data-experimentation-and-ml-platform.md`](../features/data-experimentation-and-ml-platform.md).

## Aggregate clusters inside it

17 tables across 4 clusters. `training_dataset_manifests` sits in `model` rather than `feature`
because a manifest exists to make one model version reproducible; it is a fact about that model, not
a reusable feature artifact.

## What it does not own

The decision a prediction informed — `trust` owns `risk_model_predictions` and `risk_decisions`,
`discovery` owns `ranking_model_versions`, and both deliberately hold no foreign key back into this
module. The linkage is by identifier and digest, which is what lets a model be retired without
invalidating the decisions it once advised. Nor does it own the features' source data (`analytics`).

## Public API

No live service exists yet. `hostops` is the only known consumer (3 FKs). The eventual API is a
prediction-request and model-metadata surface; `online_feature_values` and `model_versions` must
never be reachable as entities from outside, since an approval gate that another module can bypass
is not a gate.

## Allowed dependencies

None with live code today. Schema carries 9 foreign keys into `analytics` and no others.

## Data coupling `verify()` cannot see

Inbound: `hostops` (3). Outbound: `analytics` (9). This is the cleanest coupling profile of any
non-foundation module — one outbound target, one inbound source — and it is still invisible to
`ApplicationModules.verify()`, per
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## What it leaves open

Whether `trust`'s `risk_feature_definitions` and `risk_feature_snapshots` should eventually be served
by this module's feature store rather than duplicated inside `trust` is left open. Today they are
separate deliberately: a risk feature must remain computable when the ML platform is unavailable,
and `trust` holds no foreign key into `ml`.

## Exit criteria

- All 17 tables and their entities/repositories live under `dev.ngb.backend.ml.internal.model` /
  `.repository`, in the four clusters above.
- `ApplicationModules.verify()` passes with `ml`'s only declared dependency being `analytics`.
