# `review`

## Goal

Own the right to review a stay, the review's own revision and publication lifecycle, and the aspect
and reputation intelligence built on top of it, as one module distinct from the general-purpose
`trust` content-moderation pipeline it happens to sit next to in the schema.

## Forces that shaped it

- **It owns migration `026` in full**, with no merge candidate: the feature doc that groups
  `messaging` and `stay` together does not extend to `review`, and no bidirectional FK cycle ties
  `review` to any neighbor the way `identity`'s or `booking`'s merges required.
- **`review_revisions` is the dominant root** (in-module in-degree 9), with `review_records` close
  behind (8) — a review's content and its revision history are modeled as two aggregates the same
  way `booking`'s contract and revision chain are, and for the same reason: revision *is* review
  state.
- **`aspect_taxonomy_versions` (5) anchors a genuinely separate concern** — the aspect/reputation
  intelligence built from reviews, not the review-writing workflow itself — which is why it gets its
  own cluster rather than being folded into `record`.

## What it owns

- **`right` cluster** — `review_rights` (root), `review_cycles`.
- **`record` cluster** — `review_records` (root), `review_revisions`, `review_category_values`,
  `review_media`, `review_translations`, `review_private_feedback`, `review_helpful_votes`,
  `review_interaction_events`, `review_moderation_applications`.
- **`publication` cluster** — `review_publications` (root), `review_responses`,
  `review_response_revisions`, `review_public_aggregates`, `review_aggregate_contributions`.
- **`aggregate` cluster** — `reputation_policy_versions` (root), `contextual_reputation_views`,
  `host_review_profiles`, `listing_quality_profiles`, `reviewer_attention_profiles`,
  `reviewer_attention_values`.
- **`aspect` cluster** — `aspect_taxonomy_versions` (root), `aspect_definitions`,
  `aspect_profile_versions`, `aspect_profile_values`, `review_aspect_mentions`,
  `review_extraction_runs`.

See [`../data-model/026-reviews-and-reputation.md`](../data-model/026-reviews-and-reputation.md) and
[`../features/review-reputation-and-aspect-intelligence.md`](../features/review-reputation-and-aspect-intelligence.md).

## Aggregate clusters inside it

29 tables across 5 clusters — the fourth-largest module. `record` is the largest cluster because a
review's translations, media, private feedback, and helpfulness signal are all facts *about* one
review record, not separate aggregates.

## What it does not own

Whether reviewed content gets flagged or removed for policy reasons (`trust`'s moderation pipeline,
not this module's `review_moderation_applications`, which is a review-specific workflow rather than
general content moderation) or who the reviewer/host account is (`identity`, referenced 23 times).

## Public API

No live service exists yet. `discovery` and `hostops` are already known future consumers (see below),
so the eventual API is a reputation/aspect-score lookup surface, not raw entity access.

## Allowed dependencies

None with live code today. Schema carries 23 foreign keys into `identity`, 7 into `supply`, 4 into
`booking`, 1 into `market`.

## Data coupling `verify()` cannot see

Inbound: `discovery` (2), `hostops` (1). Outbound: `identity` (23), `supply` (7), `booking` (4),
`market` (1). The 23 foreign keys into `identity` are the largest single concentration of
schema-level coupling this module has — reviewer, subject, and responder are all account holders —
and none of it is visible to `ApplicationModules.verify()`; see
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## What it leaves open

Whether `aspect`-cluster intelligence (extraction runs, taxonomy versions) should eventually be its
own module once ML involvement grows is left open; today it is a cluster of this module because its
only consumer is `review` itself.

## Exit criteria

- All 29 tables and their entities/repositories live under `dev.ngb.backend.review.internal.model`
  / `.repository`, in the five clusters above.
- `ApplicationModules.verify()` passes with `review`'s only declared dependencies being `identity`,
  `supply`, `booking`, and `market`.
