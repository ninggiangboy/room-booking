# Roadmap

What is designed in
[`../../features/identity-accounts-and-access.md`](../../features/identity-accounts-and-access.md)
(D01) but not built. This page summarizes and links rather than restating that document's full
detail; read it directly for the complete rationale, the proposed-operations table, the revocation
matrix, error semantics, event contracts, and assurance-level table.

## Administrator suspend / reactivate — Built

`AdminController.updateAccountStatus` (`PUT /api/v1/admin/users/{userId}/status`) and
`AdminAccountService` move a holder between `ACTIVE` and `SUSPENDED`, mirroring
`UserAccountService`'s revoke-sessions-and-tokens pattern and writing an `account.suspended` /
`account.reactivated` row to the audit trail below. `CLOSED` stays reachable only through
self-service closure — this endpoint rejects any transition to or from it. Bootstrapping the first
`ADMIN` grant is still manual: nothing in the product issues one yet, so an operator inserts the
first `capability_grants` row directly.

## Session inventory and sign-out-everywhere — Built

`GET /api/v1/users/me/sessions`, `DELETE /api/v1/users/me/sessions/{sessionId}`, and
`DELETE /api/v1/users/me/sessions` expose the list, a targeted revoke, and sign-out-everywhere,
built on `AuthSessionRepository.findLiveForHolder` and the existing
`RefreshTokenService.revokeAllSessionsForHolder` plus a new `revokeSession`. `AuthController.login`
and `.registerUser` now capture `client_descriptor` (the request's `User-Agent`, truncated) and
`origin_hash` (a digest of the remote address) so the device list is no longer empty.

## Step-up, reauthentication, and multi-factor

`auth_sessions.assurance_level` and `last_assurance_proof_at` are written at session creation
(`AAL1`) but nothing ever reads them to demand a stronger proof before a sensitive action, nothing
issues an `AAL2`/`AAL3` session, and `auth_credentials` supports `TOTP`/`WEBAUTHN`/`RECOVERY_CODE`
types that nothing ever enrolls.

## Organizations and co-host delegation

`organization_members` exists in the schema (migration `014`) with no reader or writer anywhere in
the codebase. `capability_grants.derived_from_grant_id` and
`CapabilityGrantService.revoke`'s cascade exist specifically so a future delegation feature (an
organization owner granting a co-host a property-scoped subset of its own authority) has somewhere
to attach without revisiting the revocation mechanism.

## Contact-channel management and phone verification

`contact_channels` supports a `PHONE` channel type and a `MARKETING`/`BILLING`/`OPERATIONS` purpose
taxonomy; only `ACCOUNT`-purpose `EMAIL` channels are ever created, by registration. No endpoint
lets a holder add, verify, or manage additional channels.

## `auth_attempts` velocity control — Built

`AuthenticationService.login` writes an `AuthAttempt` row (via a new `AuthAttemptService`/
`AuthAttemptFactory`) for every attempt and checks both the per-account and per-identifier failure
count in the rolling window before evaluating credentials, rejecting with `429
AUTH_ATTEMPT_RATE_LIMITED` once either reaches the configured threshold
(`app.auth-attempts.*`). Fixed a latent bug found while wiring this up: `AuthAttempt.userId` and
`AuthAttemptRepository` still referenced the `user_id` column migration `037` renamed to
`account_holder_id`, unnoticed only because nothing had used the repository yet. Deliberately not
wired into refresh, password reset, or email verification — refresh has no user-supplied identifier
to limit against, and the other two already have their own cooldown.

## Capability restrictions in practice — Built (single-capability only)

`CapabilityRestrictionService`/`CapabilityRestrictionFactory` are the only writer of
`capability_restrictions`, exposed as `POST/DELETE/GET /api/v1/admin/capability-restrictions`.
Fixed a second latent bug found while wiring this up: `ck_capability_restrictions_principal` still
allowed only `USER`, though migration `037` renamed the shared `PrincipalType` enum's `USER` value
to `PERSON` for `capability_grants` and never repointed this table — see migration `038`.
Capability-*group* restrictions remain out of scope; no group-to-capability taxonomy exists, so
`AuthorizationService.effectiveCapabilities` still only subtracts single-capability restrictions.

## Identity audit trail — Built

A new `platform.AuditTrailWriter` port (implementation in `platform.internal.service.audit`,
matching the `EmailSender`/`SmtpEmailSender` pattern) is the only writer of `audit_events` reachable
from outside `platform`. Wired into the workflows added by this pass — admin suspend/reactivate,
self-service session revoke, and capability restriction issue/lift — rather than retrofitted onto
every pre-existing mutation. Fixed a third latent bug found while wiring this up: `AuditEvent` (like
`AuthAttempt`) has no `@Version`, so Spring Data JDBC's `isNew()` check falls back to "is the `@Id`
null"; a factory that pre-assigns the id the way every other factory in this codebase does makes it
issue a silent, zero-row `UPDATE` instead of an `INSERT`. Both factories now leave `id` unset and
let the database's own `DEFAULT gen_random_uuid()` generate it.

## Identity events through the outbox

`EmailVerificationIssued` and `PasswordResetIssued` are deliberately *not* on Spring Modulith's
`@ApplicationModuleListener`/outbox registry, for the reason recorded in
[`../../modules/identity.md`](../../modules/identity.md). D01 proposes durable, non-secret-bearing
identity facts (account created, session revoked, capability granted) published through that outbox
so other domains (`trust`, `admin`) can react without polling.

## Erasure

Account closure (`DELETE /api/v1/users/me`) sets `status = CLOSED` and revokes sessions/tokens; it
does not anonymize or erase personal data. D01's target design adds a `DELETION_REQUESTED`
intermediate state and an erasure/anonymization workflow coordinated with D22's legal-hold and
retention primitives.

## `PENDING_VERIFICATION` state

D01's target account lifecycle includes a `PENDING_VERIFICATION` state distinct from `ACTIVE`
for an account whose primary channel is unverified. The live schema only has
`ACTIVE`/`SUSPENDED`/`CLOSED`; an unverified account is fully `ACTIVE` today, which is a narrower
lifecycle than the target design describes.

## Market resolution

Every account holder is created with `context_state = UNRESOLVED` (see
[`01-registration.md`](01-registration.md)). No workflow exists to resolve a holder's market, so
`AccountHolder.canTransact()` is false for every account this application has ever registered.
`AccountHolderRepository.findAllByContextStateOrderByCreatedAtAsc(UNRESOLVED)` exists for an
operator tool that does not yet exist to consume it.

## Status

Administrator suspend/reactivate, session inventory and sign-out-everywhere, `auth_attempts`
velocity control, capability restrictions (single-capability), and the identity audit trail are
**Built**. Everything else on this page is still **Planned**: designed in D01, in some cases
already scaffolded in the schema, but with no live service or endpoint behind it.
