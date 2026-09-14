# `discovery`

## Goal

Own the read side of search and recommendation — ranking policy, epoch, exposure, and the listing and
guest profiles they score — as a module that is entirely derived and rebuildable, and that reads
without ever deciding.

## Forces that shaped it

- **Everything here is a projection.** Migration `029` states it directly: discovery reads and never
  decides. That is the module's defining property, not a stylistic note — it means every table in it
  can be dropped and rebuilt from the modules upstream, which makes `discovery` the cheapest module
  in the system to get wrong and the one whose boundary violations would be least visible.
- **`ranking_policy_versions` is the root** (in-module in-degree 4) rather than any of the profile
  tables, because an exposure is only interpretable against the policy, model, and epoch that
  produced it — `ranking_exposures` alone references seven other in-module tables.
- **Erasure is a first-class cluster, not a column.** `personalization_erasure_directives` and their
  applications exist because opt-out must be enforced where a batch rebuild cannot undo it. Folding
  them into `profile` would bury the one part of this module that is not safely rebuildable.

## What it owns

- **`ranking` cluster** — `ranking_policy_versions` (root), `ranking_model_versions`,
  `ranking_epochs`, `ranking_exposures`, `ranking_exposure_reasons`, `recommendation_reason_codes`,
  `exploration_budget_windows`.
- **`profile` cluster** — `listing_discovery_profiles` (root), `listing_discovery_features`,
  `listing_outcome_aggregates`, `guest_preference_profiles`, `guest_preference_features`,
  `discovery_market_priors`.
- **`event` cluster** — `discovery_search_requests` (root), `discovery_events`,
  `guest_session_intents`.
- **`personalization` cluster** — `personalization_erasure_directives` (root),
  `personalization_erasure_applications`, `personalization_settings`, `saved_listings`.

See [`../data-model/029-discovery-projections.md`](../data-model/029-discovery-projections.md) and
[`../features/personalized-discovery.md`](../features/personalized-discovery.md).

## Aggregate clusters inside it

20 tables across 4 clusters. `saved_listings` sits in `personalization` rather than `profile`
because it is explicit guest intent — which, per the platform invariant that explicit intent
outranks inferred preference, is governed by the same erasure directives as the rest of the cluster,
not by the rebuildable profile machinery.

## What it does not own

The ranking model itself (`ml`, whose `model_versions` is the governed artifact;
`ranking_model_versions` here is the discovery-side pointer at one), the listing being ranked
(`supply`), or the reputation signal feeding a score (`review`, referenced twice).

## Public API

No live service exists yet. `growth` is the only known consumer (1 FK, into `saved_listings`), so
the eventual API is a candidate-retrieval and saved-listing surface rather than access to profiles
or exposures.

## Allowed dependencies

None with live code today. Schema carries 11 foreign keys into `identity`, 9 into `supply`, 7 into
`market`, 2 into `review`.

## Data coupling `verify()` cannot see

Inbound: `growth` (1). Outbound: `identity` (11), `supply` (9), `market` (7), `review` (2). Note what
is *absent*: no foreign key into `ml`, despite this module scoring with models — the linkage is by
identifier and digest rather than by constraint, which is deliberate and recorded in migration `029`
as ranks carrying an epoch, policy, model, and feature digest. None of this is visible to
`ApplicationModules.verify()`; see
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## What it leaves open

Whether `discovery` should consume upstream change as events rather than rebuild from source tables
is left open. It is the module where `@ApplicationModuleListener` would pay off most — a projection
is exactly what an at-least-once post-commit listener is for — but nothing here has a service layer
to receive one yet.

## Exit criteria

- All 20 tables and their entities/repositories live under `dev.ngb.backend.discovery.internal.model`
  / `.repository`, in the four clusters above.
- `ApplicationModules.verify()` passes with `discovery`'s only declared dependencies being
  `identity`, `supply`, `market`, and `review`.
