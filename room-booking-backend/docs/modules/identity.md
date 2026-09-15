# `identity`

## Goal

Own the account holder, its credentials, its sessions, and what it is permitted to do — the single
most FK-referenced concept in the schema — and carry the only substantial live application code
that exists in this codebase today: registration, login, email verification, password reset,
refresh-token rotation, and host onboarding, which grants the `HOST` capability.

Migration `037` retired the legacy `users`/`user_roles`/`host_profiles` schema that migration `001`
created and that the rest of this document used to describe as still live. `account_holders` is now
the module's sole principal root.

## Forces that shaped it

- **`account_holders` is the single most FK-referenced table in the schema.** 223 foreign keys
  already pointed at it before migration `037`, and `037` repointed roughly twenty more that used to
  point at the now-dropped `users`. Every module that references a principal references this table.
- **This is the only module with running services**, across the aggregates that survive:
  `AccountHolder`, `ContactChannel`, `AuthCredential`, `AuthSession`, `AuthToken`,
  `CapabilityGrant`, `CapabilityRestriction`, `OrganizationMember`. `HostOnboardingService` looks
  like [`hostverification`](hostverification.md)'s at a glance — same name, same onboarding subject
  — but it issues a `HOST` capability grant, not a `hostverification`-owned row; see "What it does
  not own" below.

## What it owns

- **`account` cluster** — `account_holders` (the sole principal root), `organization_members`,
  `contact_channels`.
- **`credential` cluster** — `auth_credentials`.
- **`session` cluster** — `auth_sessions`, `auth_tokens`, `auth_attempts`.
- **`capability` cluster** — `capability_grants`, `capability_restrictions`.

Live code: `service/auth/*` (`AuthenticationService`, `AccessTokenService`, `RefreshTokenService`,
`EmailVerificationService`, `PasswordResetService`, `AuthTokenFactory`, `UserRegistrationFactory`,
`AuthEmailNotifier`), `service/account/{UserAccountService,AccountHolderFinder}`,
`service/authz/{Capability,RoleBundle,AuthorizationService,CapabilityGrantService}`,
`service/validation/PasswordPolicy`, `service/host/HostOnboardingService`,
`controller/AuthController`, `controller/UserController`, `event/EmailVerificationIssued`,
`event/PasswordResetIssued`, and the module-root `IdentityFacts`. These live under
`identity.internal.service.{auth,account,authz,validation,host}` and `identity.internal.web`,
preserving the existing package-private visibility of `AuthTokenFactory` and
`UserRegistrationFactory`.

## Aggregate clusters inside it

Three clusters across 9 tables (down from four clusters across 12 before migration `037` dropped
`host_profiles`, `user_roles`, and `users`). `account_holders` dominates the internal FK graph; the
other roots (`auth_tokens`, `capability_grants`, `contact_channels`, `auth_sessions`) each anchor a
small satellite of their own.

## What it does not own

`identity` does not own the target-model verification a host must pass to be trusted with real
supply and payouts — that is `hostverification`'s `host_legal_profiles` lifecycle (migration `015`)
— nor the public host profile (`bio`, rating, review count), which belonged to nobody's live code
after migration `037` dropped `host_profiles`; whichever module eventually owns it starts from a
clean slate rather than inheriting `identity`'s old table. `identity` also does not own the
policies that decide what a risk-flagged account may do (`trust`); it owns only who someone is and
what they are authenticated to do.

See [`../data-model/001-identity.md`](../data-model/001-identity.md),
[`../data-model/014-identity-target-model.md`](../data-model/014-identity-target-model.md),
[`../data-model/037-identity-retire-legacy-tables.md`](../data-model/037-identity-retire-legacy-tables.md),
and [`../features/identity-accounts-and-access.md`](../features/identity-accounts-and-access.md).

## Public API

- `AccessTokenService` — promoted to `identity`'s root package (not `internal/`) specifically because
  `config`'s `JwtAuthenticationFilter` is a genuine external consumer of token verification. See
  "What changes for `JwtAuthenticationFilter`" below.
- `IdentityFacts` — promoted alongside `AccessTokenService`, for the identical reason: the filter
  must reload a principal's status and effective capabilities from PostgreSQL on every request
  rather than trusting the JWT's own (now roleless) claims, and that reload is a genuine external
  consumer of identity state, not an internal collaborator. `AccessTokenService.generateAccessToken`
  no longer writes a `roles` claim; it writes `sid` (the session id) instead.
- `AccountHolderFinder`-shaped lookups for "load this account and decide what an absent or disabled
  one means," the precedent already established in
  [`../conventions/01-architecture-and-layering.md`](../conventions/01-architecture-and-layering.md).
- `EmailVerificationIssued` and `PasswordResetIssued` remain published events, but deliberately do
  **not** move to the `@ApplicationModuleListener` registry — see below.

Everything else — `AuthenticationService`, `RefreshTokenService`, `EmailVerificationService`,
`PasswordResetService`, the two factories, `UserAccountService`, `AuthorizationService`,
`CapabilityGrantService`, `PasswordPolicy` — stays under `internal/service/...` and is invisible
outside this module.

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

- `market`: `identity`'s schema carries 2 foreign keys into `market`'s tables. No live code crosses
  this boundary yet.
- `platform`: `service/mail/*` moves to `platform.internal.service.mail`, with its port,
  `EmailSender`, promoted to `platform`'s root — the same promotion `AccessTokenService` needed, for
  the same reason: `AuthEmailNotifier` is a genuine external consumer, not an internal collaborator
  of `platform`'s mail adapter. `identity`'s auth services depend on `platform`'s exception base
  types and `util` the same way they always have.
- `config`: `identity`'s own `ApiErrorResponse` usage points at `config`, which owns that DTO now
  (see [`config.md`](config.md)) — the one dependency in this module that runs in the direction a
  reader might not expect, since `config` is usually the dependent, not the dependency.

No other module may be imported by anything under `identity.internal`, and `hostverification` is not
one of `identity`'s dependencies despite the historical proximity of their onboarding code — see "The
legacy host-onboarding workflow" above.

## Data coupling `verify()` cannot see

`identity` is referenced by more foreign keys than any other module: `support` (63), `review` (23),
`admin` (22), `trust` (20), `stay` (18), `growth` (16), `booking` (16), `pricing` (10), `ledger`
(10), `discovery` (11), `messaging` (12), `hostops` (6), `hostverification` (4), `supply` (4),
`payment` (3). System-wide, `account_holders` alone is the target of at least 164 foreign keys
outside its own migration group (as counted before migration `037`; `037` repointed several more of
`supply`'s and `booking`/`review`/`trust`-engagement's early foreign keys at `account_holders`,
which only grows this number). None of this is visible to `ApplicationModules.verify()`; it is
recorded here so a reviewer changing this module's schema understands the blast radius a passing
build will not reveal.

## Resolved: `users` versus `account_holders`

This question used to be open: 223 foreign keys pointed at `account_holders` and only 13 at `users`,
while all live application code still addressed `users`. Migration `037` resolved it in the
direction the schema had already voted: `account_holders` is now the sole principal root, and every
foreign key that used to point at `users` points at `account_holders`. Resolving it stayed a change
entirely internal to `identity` plus two named tables outside it
(`verification_appeals.submitted_by`, `property_collaborators.user_id`) — a data-model and service
change, not a cross-module contract change, exactly as this document predicted it would be.

## What it leaves open

`Capability`/`RoleBundle`/`AuthorizationService`/`CapabilityGrantService` exist and are the live
authorization mechanism, but nothing yet issues a resource-scoped grant (only `GLOBAL`-scoped
`GUEST`/`HOST` grants exist), nothing issues a `capability_restrictions` row, and delegation
(`derived_from_grant_id`) has no caller. See
[`../implementation/identity/09-roadmap.md`](../implementation/identity/09-roadmap.md) for what is
designed but not built.

## Exit criteria

- All 9 tables and their entities/repositories live under `dev.ngb.backend.identity.internal.model`
  / `.repository`, in the three clusters above.
- `service/auth`, `service/account`, `service/authz`, `service/validation`, `service/host`, the two
  auth controllers, and the two auth events live under `identity.internal.service.*` /
  `.internal.web` / root, with `AccessTokenService` and `IdentityFacts` at the module root and every
  factory (`AuthTokenFactory`, `UserRegistrationFactory`) still package-private.
- `EmailVerificationIssued`/`PasswordResetIssued` remain on `@TransactionalEventListener`, documented
  as the named exception in `AuthEmailNotifier`'s Javadoc.
- `ApplicationModules.verify()` passes with `identity`'s only declared dependencies being `market`,
  `platform`, and `config`.
