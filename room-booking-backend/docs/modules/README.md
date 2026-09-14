# Modules

This directory is the binding map of `dev.ngb.backend`'s Spring Modulith modules, the module-layer
counterpart to [`../conventions/`](../conventions/README.md). Where a module document and the code
disagree, the code is authoritative and this document must be corrected in the same change; where a
module document and `../conventions/` disagree on a cross-cutting rule (layering, naming, JavaDoc),
`../conventions/` wins.

Read [`../architecture/modular-monolith.md`](../architecture/modular-monolith.md) first for why this
split exists and what Spring Modulith does and does not check. This document is the index into the
22 modules it produced.

## The modules

| Module | Owns | Depended on by (real, Java) | Type |
| --- | --- | --- | --- |
| [`platform`](platform.md) | Idempotency, outbox/inbox, audit, external references, the shared kernel | every module (shared kernel), `config` | shared |
| [`config`](config.md) | Security, OpenAPI, JDBC conversion, auditing, exception handling wiring | — (nothing depends on `config`; it depends on everything) | open |
| [`market`](market.md) | Market, legal entity, provider account, policy bundle, localized content | `identity`, and every downstream module through it | closed |
| [`identity`](identity.md) | Account holder, session, credential, capability grant, the running auth stack | `hostverification`, `supply`, `pricing`, `payment`, `ledger`, `booking`, `messaging`, `stay`, `review`, `discovery`, `trust`, `admin`, `hostops`, `growth`, `support` | closed |
| [`hostverification`](hostverification.md) | Seller KYC/KYB, screening, tax, licensing, payout destination | `ledger` | closed |
| [`supply`](supply.md) | Property, accommodation type, physical unit, listing, rate plan, geo catalog | `inventory`, `pricing`, `booking`, `discovery`, `review`, `messaging`, `stay`, `hostops`, `growth`, `support` | closed |
| [`inventory`](inventory.md) | Availability day, hold, claim, block, iCal sync | `booking`, `pricing`, `hostops`, `stay` | closed |
| [`pricing`](pricing.md) | Price rule, promotion, quote, tax | `booking`, `ledger`, `growth`, `hostops` | closed |
| [`payment`](payment.md) | Provider-independent payment state, webhook, refund execution, dispute gateway | `booking` (via `ledger`), `ledger`, `support` | closed |
| [`ledger`](ledger.md) | Double-entry accounting, host payable, payout, statement, reconciliation | `booking`, `growth`, `support` | closed |
| [`booking`](booking.md) | Stay contract plus its revision, cancellation, modification, refund-instruction, relocation chain | `payment`, `ledger`, `messaging`, `stay`, `review`, `discovery`, `hostops`, `growth`, `support` | closed |
| [`messaging`](messaging.md) | Conversation, message, notification intent, delivery | `stay`, `growth`, `support` | closed |
| [`stay`](stay.md) | Operational stay, access grant, task, incident, evidence | `support` | closed |
| [`review`](review.md) | Review right, revision, publication, aspect intelligence, reputation | `discovery`, `hostops` | closed |
| [`trust`](trust.md) | Risk signal/decision/enforcement, challenge, restriction, moderation | — | closed |
| [`support`](support.md) | Support case, evidence custody, damage claim, remedy, appeal | — | closed |
| [`discovery`](discovery.md) | Search/recommendation projection, ranking epoch, exposure | `growth` | closed |
| [`analytics`](analytics.md) | Event/dataset contract, lineage, quality, metric, experiment | `ml`, `hostops`, `growth` | closed |
| [`ml`](ml.md) | Feature store, label, model registry, prediction | `hostops` | closed |
| [`admin`](admin.md) | Operator role, break-glass, configuration, change request, feature flag | — | closed |
| [`hostops`](hostops.md) | Host metric, benchmark, forecast, advice, bulk edit | — | closed |
| [`growth`](growth.md) | Program, referral, stored value, loyalty, campaign, affiliate | — | closed |

Counts (types / repositories) and the exact aggregate clustering inside each module are in the
module's own document, not repeated here — this table is an index, not a duplicate of it.

"Depended on by (real, Java)" lists modules whose code is expected to hold a Java-level dependency —
mostly through the shared-kernel and `@NamedInterface` rules below, since as of this migration no
module has a service layer to create real dependencies yet except the four described in
[`identity.md`](identity.md) and [`hostverification.md`](hostverification.md). It is not the same
list as "what a table in this module's schema is foreign-keyed from" — that list is much larger, is
invisible to `ApplicationModules.verify()`, and is recorded per module in its own document's
"Data coupling `verify()` cannot see" section instead, per
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## Non-negotiables

1. **A module's public API is its root package only.** Everything under `internal/` — including a
   `public` type — is invisible to every other module and enforced by `ApplicationModules.verify()`,
   not just by convention. A different module that needs something from `internal/` gets a new
   record type or `@NamedInterface` in the root package; it never gets the entity itself.
2. **No entity lives in a module's `internal/model` root.** `internal/model/` is subdivided by
   aggregate root, one sub-package each; a flat `internal/model/` package is the exact problem this
   migration exists to remove one level down.
3. **`platform` and `config` are the only two modules with a special `type`.** `config` is
   `Type.OPEN`; `platform` is the shared kernel referenced through `@Modulithic(sharedModules =
   {"platform", "config"})`. Every other module is closed and reachable only through its declared
   API and `allowedDependencies`.
4. **A shared type defaults to staying with its natural owner, not to moving to `platform`.**
   Apply the R0–R3 rules in [`platform.md`](platform.md#shared-type-rules) in order; `platform`
   is the last resort, not the first guess, because a type that lives there cannot be changed
   without touching every module that depends on it.
5. **`@ApplicationModuleListener` is the default for a new event between modules.** The two
   existing events that carry a raw secret are the named exception; see
   [`identity.md`](identity.md) and
   [`../architecture/event-publication-registry.md`](../architecture/event-publication-registry.md).
   No event may carry a secret, PII, or anything without a retention policy through the registry.
6. **A new cross-module foreign key is a design decision.** It will not fail `verify()` — see the
   boundary Modulith cannot see, above — so it is reviewed the way a new `allowedDependencies` entry
   would be, and recorded in the referencing module's "Data coupling `verify()` cannot see" section.
7. **No `scanBasePackages`, no `@EnableJdbcRepositories`.** Both anchor implicitly on
   `RoomBookingBackendApplication`'s package today; declaring either replaces that implicit
   "everything under `dev.ngb.backend`" scan with an explicit list that someone will eventually
   forget to keep current.

## Using this as a checklist

Adding the 424th table: find its migration file, find the module that migration group belongs to in
the table above, then read that module's "Aggregate clusters" section to find which existing cluster
FK-references the new table most, or start a new cluster if none does. Add its entity, repository,
and (once the module has services) its workflow sub-package there — never at the module's package
root.
