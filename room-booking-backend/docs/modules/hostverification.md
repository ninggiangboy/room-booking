# `hostverification`

## Goal

Own everything that decides whether a host may sell on the platform and where their payouts may
legally go — KYC/KYB, screening, tax identity, licensing, and payout-destination eligibility — as
its own module rather than as a corner of `identity`, because "who someone is" and "what they are
eligible to do as a seller" are decided by different evidence, on different timelines, by different
authorities.

## Forces that shaped it

- **It owns migration `015` in full, with no merge candidate.** Nothing else in the schema shares a
  foreign-key cycle with it strong enough to force a merge the way `identity`'s four migrations or
  `booking`'s three were forced together.
- **It has no live service, despite `service/host/` sitting next to its schema in the flat layout
  this migration replaces.** `HostOnboardingService` and `HostProfileFactory` operate on
  `host_profiles` — the legacy table from migration `001`, owned by `identity` — not on this
  module's `host_legal_profiles` (migration `015`). Building the move script surfaced this: the
  service imports `identity`'s `User`, its `UserRole` repository, and `UserFinder` directly, which
  would have been an illegal reach into another module's internals had the service moved here
  instead of staying beside the tables it actually touches. See
  [`identity.md`](identity.md#the-legacy-host-onboarding-workflow) for the full account.
- **`host_legal_profiles` is the clear aggregate root** (in-module in-degree 8): eligibility
  decisions, beneficial owners, and verification cases all resolve back to a host's legal profile,
  not to a generic "verification case."

## What it owns

- **`profile` cluster** — `host_legal_profiles` (aggregate root), `host_tax_identifiers`,
  `beneficial_owners`.
- **`case` cluster** — `verification_cases`, `verification_documents`, `verification_appeals`,
  `screening_checks`, `regulatory_registrations`.
- **`eligibility` cluster** — `host_eligibility_decisions`, `payout_destination_claims`.

No live code moves here. `service/host/HostOnboardingService`, its package-private
`HostProfileFactory`, and the `HostOnboardingRequest`/`HostOnboardingResponse` records all move to
`identity.internal.service.host` instead — see
[`identity.md`](identity.md#the-legacy-host-onboarding-workflow) for why.

See [`../data-model/015-host-verification.md`](../data-model/015-host-verification.md) and
[`../features/identity-accounts-and-access.md`](../features/identity-accounts-and-access.md) (host
verification is covered as part of that feature's host-onboarding scope; there is no separate
feature document for it).

## Aggregate clusters inside it

10 tables across 3 clusters, small enough that — like `market` — this clustering is the complete
picture rather than a first pass; `host_legal_profiles` is the only root with meaningful internal
fan-in.

## What it does not own

Whether a host is *permitted* to list a given property once verified — that is `supply`'s and
`admin`'s concern. It does not own general account risk scoring (`trust`) or the payout execution
itself (`ledger`, which references `payout_destination_claims` by one foreign key once a destination
is approved here).

## Public API

None yet. No service exists in this module today; nothing is exported.

## Allowed dependencies

None with live code today. Schema carries foreign keys into `identity` (the account holder a
verification case or eligibility decision belongs to) and `market` (host verification rules -- tax
identifiers, regulatory registrations -- resolved against the host's market).

## Data coupling `verify()` cannot see

`ledger` carries 1 foreign key into this module's tables (a payout referencing an approved
`payout_destination_claims` row). This is the only inbound schema coupling, and it is invisible to
`ApplicationModules.verify()` for the same reason every other cross-module foreign key is — see
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## What it leaves open

Whether verification decisions should publish an event (`HostVerified`, `HostEligibilityRevoked`) for
`supply` or `admin` to react to, rather than those modules polling this module's future public API,
is left for when either consumer gets its own service.

## Exit criteria

- All 10 tables and their entities/repositories live under
  `dev.ngb.backend.hostverification.internal.model` / `.repository`, in the three clusters above.
- No live service exists here; `service/host/*` moved to `identity` instead (see
  [`identity.md`](identity.md#the-legacy-host-onboarding-workflow)).
- `ApplicationModules.verify()` passes with `hostverification`'s only declared dependencies being
  `identity` and `market`.
