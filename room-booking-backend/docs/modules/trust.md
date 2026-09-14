# `trust`

## Goal

Own risk signal, decision, enforcement, challenge, restriction, and content moderation as one module
— the platform's general-purpose defense layer, distinct from `review`'s narrower, review-specific
moderation workflow and from `support`'s case-driven dispute resolution.

## Forces that shaped it

- **It owns migration `027` in full**, plus the two legacy tables from `006` (`reviews`, `favorites`)
  that migration `016` later dropped and never recreated — those two are gone from the live schema
  and own nothing here; only `content_items`, `content_reports`, and `entity_links` from `006`'s
  successor migration are live and belong to this module.
- **`risk_subjects` is the dominant aggregate root** (in-module in-degree 12), with `risk_decisions`
  a close second (11) — a subject accumulates signals and decisions accumulate enforcements, which is
  why `subject` and `decision` are separate clusters rather than one.
- **It has zero outgoing schema references from any other module** — `trust` is a pure sink among
  business modules: nothing else's tables are referenced by `trust`'s enforcement machinery, and
  nothing points back into `trust` from outside it either. It is the most self-contained large module
  in the map.

## What it owns

- **`subject` cluster** — `risk_subjects` (root), `risk_signal_subjects`.
- **`signal` cluster** — `risk_signals` (root), `risk_velocity_counters`, `risk_velocity_contributions`.
- **`feature` cluster** — `risk_feature_definitions` (root), `risk_feature_snapshots`,
  `risk_model_predictions`.
- **`decision` cluster** — `risk_decisions` (root), `risk_decision_rule_hits`,
  `risk_decision_enforcements`, `risk_policies`, `risk_policy_approvals`,
  `moderation_decisions`, `moderation_assessments` (general content moderation, grouped here
  because both resolve against the same policy machinery as a risk decision).
- **`intervention` cluster** — `risk_restrictions` (root), `risk_challenges`,
  `risk_challenge_attempts`, `risk_protected_actions`, `risk_appeals`, `risk_access_audit`.
- **`review` cluster** — `risk_review_tasks` (root), `risk_review_queues`, `risk_review_actions`.
- **`content` cluster** — `content_items` (root), `content_revisions`, `content_reports`,
  `entity_links`.
- **`label` cluster** — `risk_labels` (root), `risk_label_taxonomy_versions`.

See [`../data-model/027-trust-safety-and-moderation.md`](../data-model/027-trust-safety-and-moderation.md)
and [`../features/trust-safety-fraud-and-moderation.md`](../features/trust-safety-fraud-and-moderation.md).

## Aggregate clusters inside it

30 tables across 8 clusters — the second-largest module by table count, kept navigable specifically
because a `risk_*`-prefixed name is not itself a grouping signal here: 24 of the 30 tables share that
one prefix, and clustering by aggregate root rather than by prefix is what keeps this module from
becoming a second flat 30-type package one level down.

## What it does not own

Review-specific moderation of review content (`review`'s own `review_moderation_applications`) or
case-driven dispute resolution with a human agent working a queue (`support`) — `trust` is the
automated/policy layer that produces signals and decisions; `support` is where a human resolves a
case that a signal or a customer complaint opened.

## Public API

No live service exists yet. The eventual API is a subject-risk lookup (`RiskLookup`-shaped) that other
modules query before allowing a sensitive action, rather than any module reading `trust`'s tables
directly.

## Allowed dependencies

None with live code today. Schema carries 20 foreign keys into `identity`, 9 into `market`.

## Data coupling `verify()` cannot see

None inbound — no other module's tables reference `trust`'s tables, which is unusual for a module
this large and worth preserving rather than treating as an oversight: `trust` is meant to be
consulted, not depended on structurally. Outbound: `identity` (20), `market` (9). See
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## What it leaves open

Whether `trust` should expose an event other modules subscribe to (`AccountRestricted`,
`ContentRemoved`) instead of being polled is left open until its service layer exists; given it has
no inbound schema coupling today, an event-based API may be the more natural first public surface
here than anywhere else in the map.

## Exit criteria

- All 30 tables and their entities/repositories live under `dev.ngb.backend.trust.internal.model` /
  `.repository`, in the eight clusters above.
- `reviews` and `favorites` (migration `006`, dropped by `016`) are confirmed absent from the live
  schema and are not migrated as entities.
- `ApplicationModules.verify()` passes with `trust`'s only declared dependencies being `identity` and
  `market`.
