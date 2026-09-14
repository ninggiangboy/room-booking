# `platform`

## Goal

Give the 21 business modules a place for the handful of concepts that belong to none of them —
durable transport primitives (idempotency, outbox, inbox, audit, external references) and the small
shared kernel of value types and enums that more than one module's aggregates need — without turning
that place into a dumping ground for anything two modules happen to both use.

## Forces that shaped it

- **Migration `012` ("platform primitives") already drew this line at the schema level.** Its five
  tables — `command_idempotency_records`, `outbox_events`, `consumer_inbox_receipts`,
  `audit_events`, `external_resource_references` — exist precisely because idempotent command
  handling, at-least-once outbound publication, at-least-once inbound consumption, and
  who-did-what-when are cross-cutting concerns no single business domain owns. `platform` is the
  Java-module expression of that same boundary.
- **The shared kernel is measured to be small, and it must stay that way.** Of 1,282 types, 1,239
  belong to exactly one module by transitive FK closure; the remaining ~39 are the entire shared
  kernel. An enum that lives in `platform` cannot be changed without potentially touching every
  module that depends on it, so `platform` is the destination of last resort, not first guess — see
  "Shared-type rules" below.
- **`platform` is the one module every other module (and `config`) may depend on.** It is the
  `sharedModules` entry Modulith is told about explicitly
  (`@Modulithic(sharedModules = {"platform", "config"})`), because unlike every other module it has
  no natural upstream/downstream direction — everything is downstream of it.

## What it owns

- **Idempotency and messaging transport**: `command_idempotency_records` (safe command retry),
  `outbox_events` (durable outbound publication envelope — aggregate type/id/version, correlation,
  causation, market, sensitivity class, dedup key, lease), `consumer_inbox_receipts` (durable inbound
  dedup).
- **Audit**: `audit_events`, keyed to an `ActorType` (see below) so a row can name a user, an
  operator, a system process, or an external provider without inventing four separate audit tables.
- **External references**: `external_resource_references`, the generic pointer to an artifact that
  lives outside the database (object storage, a provider's system of record).
- **The shared kernel** — value types and enums with no single natural owner, or whose natural owner
  cannot be allowed to become a dependency of everything else. See "Shared-type rules."
- `time/`, `util/`, and `exception/base/` move here unchanged from their current top-level packages;
  they already have no dependency on anything else in the application, which is exactly what
  qualifies a package for `platform` rather than for a business module.

See [`../data-model/012-platform-primitives.md`](../data-model/012-platform-primitives.md) and
[`../features/platform-foundation.md`](../features/platform-foundation.md) for the full schema and
contract this module implements.

## Aggregate clusters inside it

Small enough not to need `internal/model` sub-packaging by aggregate root the way the 20+-table
modules do. A flat `internal/model/` is acceptable here specifically because the module is capped by
policy (R0–R3 below) at roughly 35–40 types — the one place in this codebase where "flat" is not a
symptom of the problem this migration exists to fix, because the flatness is bounded by rule rather
than by accident.

- `internal/model/idempotency/` — `command_idempotency_records`, `consumer_inbox_receipts`
- `internal/model/outbox/` — `outbox_events`
- `internal/model/audit/` — `audit_events`
- `internal/model/reference/` — `external_resource_references`
- `internal/model/` (root) — the shared-kernel enums and value types from "Shared-type rules"
- `time/`, `util/`, `exception/base/` — unchanged, moved in as-is

## What it does not own

Anything with a natural single owner, even if the type is small or looks generic. A value type that
happens to be shaped like "a PostgreSQL column" is not automatically `platform`'s — see R1 below for
the actual test, which is narrower than "looks infrastructural."

## Public API

- The five primitive types above, for any module that needs to write an outbox event, register
  idempotency, or emit an audit row.
- The shared-kernel enums and value types, at the module root (never under `internal/`).

## Shared-type rules

Applied in order; the first rule that matches an enum or value type decides where it lives. Recorded
here because this is the one piece of the module map a future contributor will need to re-derive
correctly for the next type that looks like a candidate.

- **R0 — recompute after module merges before applying anything else.** A type that looked
  shared-across-modules when counted by migration number is often single-owner once migrations are
  grouped into modules. `PaymentFailureCategory` and `PaymentMethodFamily` (migrations `005` + `021`)
  are both `payment` now; `RefundExecutionState` (`021` + `023`) and `BookingFlowType` (`004` + `020`)
  are single-owner the same way. None of these move to `platform`.
- **R1 — a value type whose meaning is "the shape of a PostgreSQL column," not a business concept.**
  `JsonDocument`, `StayRange`, `BucketRange`. `BucketRange` is included even though only
  `ExperimentVariant` (in `analytics`) uses it today, because leaving it in `analytics` would make
  `config`'s JDBC conversion wiring depend on `analytics` — one of the most downstream modules in the
  graph. `time/`, `util/`, and `exception/base/` are the package-level version of this rule.
- **R2 — used by three or more modules with no natural single owner.** `ActorType`, `RetentionClass`,
  `SensitivityClass`, `TranslationSource`, `ObservationSource`, `ConfigurationLifecycle`,
  `MoneyPartyRole`, `AssuranceLevel`, `GovernedRegistryStatus`, `TimelineVisibility`,
  `DataPrivacyClass`, `ExceptionSeverity`, `EvidenceScanState`. These move to `platform` because no
  module in the reference list is more "the owner" than any other.
- **R3 — stays with its natural owner and is exposed through a `@NamedInterface`, when every
  consumer is already downstream of that owner.** `QuoteLineType`, `LineDirection`,
  `LineTaxTreatment`, `Refundability`, `SupplyRole` stay in `pricing.types` (`booking` already
  depends on `pricing`). `ReconciliationRunState` and `ReducerOutcome` stay in `payment.types`
  (`ledger` already depends on `payment`). **`BookingActorType` is the rule's clearest exception
  case**: five modules use it (`payment`, `ledger`, `stay`, `review`, `booking`'s own cancellation
  slice), which would satisfy R2's "≥3 modules" test, but all five are downstream of `booking`, so it
  stays in `booking.types` instead of moving here. Direction of dependency is the deciding signal,
  not the count of consumers.

The exact, exhaustive list of which of the ~39 candidate types resolved to `platform` versus stayed
with a natural owner is recorded in `../architecture/module-map.md` once Phase 2's assignment pass
runs; the rules above are what that pass applies, not a substitute for its output.

## Allowed dependencies

None. `platform` depends on nothing else in the application — that is what qualifies it to be the
one module everything else may depend on.

## Data coupling `verify()` cannot see

`admin` (3 FKs), `payment` (1 FK), and `support` (1 FK) reference `platform` tables — mostly
`external_resource_references` and `audit_events` — from their own schemas. These are ordinary
uses of the shared transport primitives and not a concern; they are recorded here only because
every cross-module FK is recorded somewhere, per
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## What it leaves open

Whether `outbox_events` ever gets a real consumer (a broker publisher) is out of scope for this
migration. `spring-modulith-starter-jdbc`'s own `event_publication` table is a different mechanism
serving a different question — see
[`../architecture/event-publication-registry.md`](../architecture/event-publication-registry.md) —
and does not retire `outbox_events`.

## Exit criteria

- `command_idempotency_records`, `outbox_events`, `consumer_inbox_receipts`, `audit_events`,
  `external_resource_references` and their entities/repositories live under
  `dev.ngb.backend.platform.internal.model` / `.repository`, clustered as above.
- `time/`, `util/`, `exception/base/` are sub-packages of `dev.ngb.backend.platform`.
- Every shared-kernel type resolved by R0–R3 that landed here is at `dev.ngb.backend.platform`'s
  root, `@NullMarked`, and nothing that resolved to R3 is duplicated here.
- `ApplicationModules.verify()` treats `platform` as a module with no `allowedDependencies` entry and
  no violation.
