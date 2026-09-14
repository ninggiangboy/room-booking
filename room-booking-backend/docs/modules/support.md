# `support`

## Goal

Own the case-driven side of dispute resolution — support case, evidence custody, damage claim,
remedy, and appeal — as one accepted-large module, because its 46 tables are one continuous life
cycle (allegation → finding → decision → money movement) rather than 46 independent facts that could
be spread across smaller modules.

## Forces that shaped it

- **It is the largest module in the map by a wide margin — 46 tables, roughly double the
  next-largest (`booking`, `trust` at 30).** It is not split further because it cannot be: its tables
  FK into each other densely and form one life cycle, not several independent ones. The migration
  plan treats this as an accepted exception rather than a failure to find the right boundary, and
  handles the size with internal clustering instead of a boundary that would just move the problem.
- **`support_cases` is the aggregate root by a wide margin** (in-module in-degree 28, more than
  double the next root); nearly every other table in this module ultimately resolves back to a case.
- **It has the densest single cross-module edge in the entire schema**: 63 foreign keys into
  `identity` — every case participant, contact, agent, and decision-maker is an account holder.

## What it owns

- **`case` cluster** — `support_cases` (root), `case_participants`, `case_contacts`,
  `case_relationships`, `case_transitions`, `case_timeline_entries`, `case_classification_history`,
  `case_assertions`, `case_notes`, `case_work_items`, `work_item_leases`, `case_sla_clocks`,
  `case_quality_reviews`, `agent_skill_grants`.
- **`decision` cluster** — `case_decisions` (root), `case_decision_findings`,
  `case_decision_approvals`, `case_findings`, `case_appeals`.
- **`evidence` cluster** — `case_evidence_items` (root), `evidence_access_log`,
  `evidence_redactions`, `evidence_transformations`, `evidence_disclosure_manifests`.
- **`claim` cluster** — `damage_claims` (root), `damage_claim_items`, `coverage_snapshots`,
  `external_claims`, `external_claim_submissions`, `external_claim_observations`,
  `payment_dispute_strategies`.
- **`remedy` cluster** — `case_remedies` (root), `case_remedy_lines`, `case_offers`,
  `case_offer_lines`, `remedy_instructions`, `remedy_reservations`, `remedy_catalog_versions`,
  `remedy_budget_windows`, `remedy_budget_consumptions`, `protection_program_versions`.
- **`queue` cluster** — `support_queues` (root), `routing_policy_versions`.
- **`policy` cluster** — `support_policy_versions` (root), `authority_policy_versions`,
  `investigation_template_versions`.

Bulk export accountability (`bulk_export_requests`, `bulk_export_accesses`) lives in `admin`, not
here, despite operating on support data — see [`admin.md`](admin.md).

See [`../data-model/028-disputes-and-support.md`](../data-model/028-disputes-and-support.md) and
[`../features/disputes-damage-claims-and-support.md`](../features/disputes-damage-claims-and-support.md).

## Aggregate clusters inside it

46 tables across 7 clusters — every table has a home; none of the 7 roots is a name-prefix grouping
of convenience. `case` is the widest cluster deliberately, because a case's participants, timeline,
notes, and work-item leases are all facts about the one case aggregate, not aggregates of their own.

## What it does not own

Whether an account is risk-flagged (`trust`'s job; a support case may be *opened by* a trust signal,
but `support` does not re-implement risk scoring) or whether a payment actually failed
(`payment_dispute_strategies` here names a strategy for a case, it does not hold `payment`'s own
dispute-gateway state, which lives in `payment`).

## Public API

No live service exists yet. Given the size of this module, its eventual API is expected to be several
narrow lookups (case status, remedy eligibility, evidence custody chain) rather than one broad
`SupportLookup`, so that a consumer only depends on the slice of `support` it actually needs.

## Allowed dependencies

None with live code today. Schema carries 63 foreign keys into `identity`, 22 into `market`, 4 into
`booking`, 4 into `messaging`, 3 into `supply`, 3 into `stay`, 2 into `payment`, 2 into `ledger`, 1
into `platform`.

## Data coupling `verify()` cannot see

None inbound — no other module's tables reference `support`'s tables. Outbound: `identity` (63),
`market` (22), `booking` (4), `messaging` (4), `supply` (3), `stay` (3), `payment` (2), `ledger` (2),
`platform` (1). The 63-edge coupling into `identity` is the single densest cross-module edge in the
whole schema, and none of it is checked by `ApplicationModules.verify()` — see
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## What it leaves open

Whether this module ever gets subdivided once it has a service layer — for instance, splitting
`claim`/`remedy` (money movement) from `case`/`evidence` (investigation) — is left explicitly open;
the migration plan accepts the size today rather than forcing a boundary the FK graph does not
support, but a future service layer may reveal a real seam the schema alone does not show.

## Exit criteria

- All 46 tables and their entities/repositories live under `dev.ngb.backend.support.internal.model`
  / `.repository`, in the seven clusters above.
- `ApplicationModules.verify()` passes with `support`'s only declared dependencies being `identity`,
  `market`, `booking`, `messaging`, `supply`, `stay`, `payment`, `ledger`, and `platform`.
