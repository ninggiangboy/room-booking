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
- **It is one of only four aggregates in the entire codebase with a live service today** —
  `HostProfile`, via `service/host/HostOnboardingService` and the package-private
  `HostProfileFactory` — so, like `identity`, this document has to describe real running code, not
  only a target schema.
- **`host_legal_profiles` is the clear aggregate root** (in-module in-degree 8): eligibility
  decisions, beneficial owners, and verification cases all resolve back to a host's legal profile,
  not to a generic "verification case."

## What it owns

- **`profile` cluster** — `host_legal_profiles` (aggregate root), `host_tax_identifiers`,
  `beneficial_owners`.
- **`case` cluster** — `verification_cases`, `verification_documents`, `verification_appeals`,
  `screening_checks`, `regulatory_registrations`.
- **`eligibility` cluster** — `host_eligibility_decisions`, `payout_destination_claims`.

Live code moving here unchanged, per Phase 3 of the migration:
`service/host/HostOnboardingService` and the package-private `HostProfileFactory`, into
`hostverification.internal.service.host` — the sub-package is kept exactly as it is today rather
than flattened, specifically so `HostProfileFactory` stays package-private and does not have to
widen to `public` just to survive the move.

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

`HostOnboardingService`'s public methods are the module's API surface today, in the same shape they
have now. No `@NamedInterface` or cross-module record type exists yet because no other module
consumes this module's internals in code today — only `ledger`'s schema references it (see below).

## Allowed dependencies

Two, both real today and unchanged by the move:

- `identity`: `HostOnboardingService`/`HostProfileFactory` resolve the account holder a host profile
  belongs to.
- `market`: host verification rules (tax identifiers, regulatory registrations) are resolved against
  the host's market.

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
- `service/host/*` moves into `hostverification.internal.service.host` with `HostProfileFactory`
  still package-private and `HostOnboardingService` the only public entry point.
- `ApplicationModules.verify()` passes with `hostverification`'s only declared dependencies being
  `identity` and `market`.
