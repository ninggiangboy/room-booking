# `identity`

## Goal

Own the account holder, its credentials, its sessions, and what it is permitted to do — the single
most FK-referenced concept in the schema — and carry forward, unmodified, the only substantial live
application code that exists in this codebase today: registration, login, email verification,
password reset, and refresh-token rotation.

## Forces that shaped it

- **A bidirectional foreign-key cycle forces migrations `001`, `007`–`009`, and `014` into one
  module.** `014` created `account_holders` alongside the original `users` table and points back to
  it nine times; `auth_tokens` (from `001`, renamed by `008`) points to `auth_sessions` (from `014`).
  Neither half can be separated from the other without breaking a live foreign key.
- **`identity` is the only exception to the "derive module from `@Table` → `CREATE TABLE`" rule.**
  `auth_tokens` has no `CREATE TABLE` of its own — changeset `008` renamed `007`'s original table —
  so it is the one entity in the entire 423-table schema assigned to a module by hand rather than by
  the mechanical lookup.
- **This is one of only two modules (with [`hostverification`](hostverification.md)) that already
  has running services**, out of four aggregates total that do (`User`, `UserRole`, `HostProfile`,
  `AuthToken`). Its module document has to be accurate about real code, not just about schema.

## What it owns

- **`account` cluster** — `users` (aggregate root, in-module in-degree 10), `account_holders`,
  `organization_members`, `contact_channels`.
- **`credential` cluster** — `auth_credentials`, `email_verification_tokens`.
- **`session` cluster** — `auth_sessions`, `auth_tokens` (hand-assigned, see above), `auth_attempts`.
- **`capability` cluster** — `capability_grants`, `capability_restrictions`, `user_roles`,
  `host_profiles`.

Live code moving here unchanged from the current flat packages, per Phase 3 of the migration:
`service/auth/*` (`AuthenticationService`, `AccessTokenService`, `RefreshTokenService`,
`EmailVerificationService`, `PasswordResetService`, `AuthTokenFactory`, `UserRegistrationFactory`,
`AuthEmailNotifier`), `service/account/UserAccountService`, `service/user/UserFinder`,
`service/validation/PasswordPolicy`, `controller/AuthController`, `controller/UserController`,
`event/EmailVerificationIssued`, `event/PasswordResetIssued`. These land under
`identity.internal.service.{auth,account,user,validation}` and `identity.internal.web`, preserving
the existing package-private visibility of `AuthTokenFactory` and `UserRegistrationFactory` exactly.

See [`../data-model/001-identity.md`](../data-model/001-identity.md),
[`../data-model/014-identity-target-model.md`](../data-model/014-identity-target-model.md), and
[`../features/identity-accounts-and-access.md`](../features/identity-accounts-and-access.md).

## Aggregate clusters inside it

Four clusters across 12 tables. `users` dominates the internal FK graph (in-degree 10); the other
roots (`account_holders`, `auth_tokens`, `capability_grants`, `contact_channels`, `auth_sessions`)
each anchor a small satellite of their own.

## What it does not own

Whether an external module should be pointing at `users` or at `account_holders` for a given fact.
That question is explicitly **not resolved by this migration** — see "The open `users` versus
`account_holders` question" below. `identity` also does not own market-specific eligibility rules
(`hostverification`) or the policies that decide what a risk-flagged account may do (`trust`); it
owns only who someone is and what they are authenticated to do.

## Public API

- `AccessTokenService` — promoted to `identity`'s root package (not `internal/`) specifically because
  `config`'s `JwtAuthenticationFilter` is a genuine external consumer of token verification. See
  "What changes for `JwtAuthenticationFilter`" below.
- `UserFinder`-shaped lookups for "load this account and decide what an absent or disabled one
  means," the precedent already established in
  [`../conventions/01-architecture-and-layering.md`](../conventions/01-architecture-and-layering.md).
- `EmailVerificationIssued` and `PasswordResetIssued` remain published events, but deliberately do
  **not** move to the `@ApplicationModuleListener` registry — see below.

Everything else — `AuthenticationService`, `RefreshTokenService`, `EmailVerificationService`,
`PasswordResetService`, the two factories, `UserAccountService`, `PasswordPolicy` — stays under
`internal/service/...` and is invisible outside this module.

### What changes for `JwtAuthenticationFilter`

Today `filter/JwtAuthenticationFilter` imports `service.auth.AccessTokenService` directly; both are
flat top-level packages, so nothing checks this. After the split, `JwtAuthenticationFilter` lives in
`config` (see [`config.md`](config.md)) and would be reaching into
`identity.internal.service.auth.AccessTokenService` — a violation `ApplicationModules.verify()` will
catch regardless of `config` being declared `Type.OPEN`. `Type.OPEN` only relaxes what a module may
depend on; it does not relax what an internal package may expose. The fix is not an allow-list entry:
`AccessTokenService` is promoted to `dev.ngb.backend.identity`'s public root package, because a JWT
filter verifying a bearer token is genuinely an external consumer of that capability, not an internal
collaborator of the auth workflow. Cost: one file moves one package level up.

### Why `EmailVerificationIssued` and `PasswordResetIssued` stay off the event registry

Both events carry `rawToken` — the secret placed in the verification/reset link and, by explicit
design, never stored anywhere in raw form (only its digest is persisted). Spring Modulith's
`@ApplicationModuleListener` serializes the full event payload into
`event_publication.serialized_event` as plaintext JSON, and with the framework's default
`completion-mode=update` that row is retained indefinitely — which would put the raw secret back into
a durable table this codebase's token design exists specifically to avoid. `AuthEmailNotifier`
therefore keeps handling both events on a bare `@TransactionalEventListener(phase =
TransactionPhase.AFTER_COMMIT)`, exactly as it does today, and its class Javadoc states this
explicitly as the reason. This is the one named exception to "`@ApplicationModuleListener` is the
default for a new event between modules" — see
[`../architecture/event-publication-registry.md`](../architecture/event-publication-registry.md) and
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md).

## Allowed dependencies

Two kinds, both real today:

- `market`: `identity`'s schema carries 2 foreign keys into `market`'s tables. No live code crosses
  this boundary yet.
- `platform`: `service/mail/*` (`EmailSender`, `SmtpEmailSender`) is used by `AuthEmailNotifier` and
  moves to `platform.internal.service.mail` per the plan; `identity`'s auth services depend on it the
  same way they do today.

No other module may be imported by anything under `identity.internal`.

## Data coupling `verify()` cannot see

`identity` is referenced by more foreign keys than any other module: `support` (63), `review` (23),
`admin` (22), `trust` (20), `stay` (18), `growth` (16), `booking` (16), `pricing` (10), `ledger`
(10), `discovery` (11), `messaging` (12), `hostops` (6), `hostverification` (4), `supply` (4),
`payment` (3). System-wide, `account_holders` alone is the target of 164 foreign keys outside its own
migration group. None of this is visible to `ApplicationModules.verify()`; it is recorded here so a
reviewer changing this module's schema understands the blast radius a passing build will not reveal.

## The open `users` versus `account_holders` question

[`../data-model/README.md`](../data-model/README.md) records that 223 foreign keys across the schema
point at `account_holders` and 13 point at `users`, while all live application code today still
addresses `users`. This migration does **not** resolve which table is the long-term identity anchor.
What it does is put both tables inside the same module, so that whichever way the question resolves,
resolving it is a change entirely internal to `identity` — a service swapping which table it reads,
not a cross-module contract change.

## What it leaves open

The `AccessTokenService` promotion above is the only public-API change this migration makes to
`identity`'s live code; everything else about how registration, login, verification, and reset work
is unchanged. Whether `capability_grants`/`capability_restrictions` need their own service ahead of
whatever consumes them (`trust`, `admin`) is left to when that consuming code is written.

## Exit criteria

- All 12 tables and their entities/repositories live under `dev.ngb.backend.identity.internal.model`
  / `.repository`, in the four clusters above; `auth_tokens` is placed here explicitly rather than
  derived.
- `service/auth`, `service/account`, `service/user`, `service/validation`, the two auth controllers,
  and the two auth events move into `identity.internal.service.*` / `.internal.web` / root, with
  `AccessTokenService` promoted to the module root and every factory still package-private.
- `EmailVerificationIssued`/`PasswordResetIssued` remain on `@TransactionalEventListener`, documented
  as the named exception in `AuthEmailNotifier`'s Javadoc.
- `ApplicationModules.verify()` passes with `identity`'s only declared dependency being `market` and
  `platform`.
