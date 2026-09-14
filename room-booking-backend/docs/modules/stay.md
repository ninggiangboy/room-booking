# `stay`

## Goal

Own what happened in the building and who was entitled to be there — operational stay, arrival
instruction, access entitlement, operational task, and incident — as the module that records
physical reality with custody, distinct from `messaging`, which records what the platform said about
it.

## Forces that shaped it

- **Not merged with `messaging`, despite a feature document pairing them.** The coupling is one
  edge (`stay → messaging`, 1 FK), nowhere near the bidirectional density that forced `identity`'s
  and `booking`'s internal merges. A shared feature narrative is not a merge argument; the FK cycle
  test is. See [`messaging.md`](messaging.md), which records the same decision from the other side.
- **`operational_stays` is the dominant root** (in-module in-degree 7) and every other cluster root
  hangs off it: `incidents`, `instruction_sets`, `operational_tasks`, and `access_grants` all
  reference it directly. This module is a single spine with four branches, not four peers.
- **Access entitlement and access credential are deliberately separate rows**, per migration `025`.
  That separation is what lets a lock outage fall back to an in-person handoff without rewriting the
  entitlement, so `access_grants` anchors its own cluster rather than folding into `instruction`.

## What it owns

- **`stay` cluster** — `operational_stays` (root), `stay_observations`, `stay_outcome_decisions`.
- **`instruction` cluster** — `instruction_sets` (root), `instruction_access_audit`.
- **`access` cluster** — `access_grants` (root), `access_operations`, `access_observations`.
- **`task` cluster** — `operational_tasks` (root), `task_evidence`, `maintenance_records`.
- **`incident` cluster** — `incidents` (root), `incident_events`, `incident_evidence_links`,
  `remedy_requests`.

See [`../data-model/025-stay-operations.md`](../data-model/025-stay-operations.md) and
[`../features/messaging-notifications-and-stay-operations.md`](../features/messaging-notifications-and-stay-operations.md).

## Aggregate clusters inside it

15 tables across 5 clusters. `instruction_access_audit` sits in `instruction` rather than in a
generic audit cluster because it answers "who was shown this sensitivity-banded instruction", which
is a fact about the instruction set, not about the platform's audit trail — that one lives in
`platform`.

## What it does not own

The damage claim or support case an incident leads to (`support`, which references this module 3
times), the booking that authorized the stay (`booking`, referenced 11 times), or the physical unit
itself (`supply`, referenced 13 times). `stay` records the operational event; the remedy, the
contract, and the asset all belong elsewhere.

## Public API

No live service exists yet. `support` is the known future consumer, so the eventual API is an
incident/evidence reference surface rather than raw access to `access_grants` — which carries
envelope-encrypted credential references and must never leave this module as an entity.

## Allowed dependencies

None with live code today. Schema carries 18 foreign keys into `identity`, 13 into `supply`, 11 into
`booking`, 4 into `market`, 1 each into `inventory` and `messaging`.

## Data coupling `verify()` cannot see

Inbound: `support` (3). Outbound: `identity` (18), `supply` (13), `booking` (11), `market` (4),
`inventory` (1), `messaging` (1). This module is almost purely downstream — 48 outbound foreign keys
against 3 inbound — which is what makes it a safe late extraction, and none of it is visible to
`ApplicationModules.verify()`; see
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## What it leaves open

Whether an access credential reveal should publish through the event publication registry is left to
this module's own design. It is the harder case of the rule in
[`../architecture/event-publication-registry.md`](../architecture/event-publication-registry.md): a
reveal event must carry the grant identifier, never the secret reference or the plaintext, because a
registry row has no retention policy.

## Exit criteria

- All 15 tables and their entities/repositories live under `dev.ngb.backend.stay.internal.model` /
  `.repository`, in the five clusters above.
- `ApplicationModules.verify()` passes with `stay`'s only declared dependencies being `identity`,
  `supply`, `booking`, `market`, `inventory`, and `messaging`.
