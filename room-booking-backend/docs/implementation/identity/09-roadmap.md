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

## Step-up, reauthentication, and multi-factor — TOTP enrollment and step-up proof built

`MfaService`, behind `POST/DELETE /api/v1/users/me/mfa/totp` and
`POST /api/v1/users/me/mfa/totp/step-up`, is `auth_credentials`' first `TOTP` writer.
Enrollment generates an RFC 6238 seed (`platform.util.TotpUtils`), seals it through a new
`platform.SecretBox` port — realizing `AuthCredential`'s documented "secret boundary" for the
first time, with a real AES-256-GCM implementation (`AesGcmSecretBox`) rather than a placeholder,
since encryption at rest needs no third-party account the way SMS delivery does — and returns the
Base32 seed and an `otpauth://` provisioning URI to the holder exactly once. Step-up verifies a
submitted code against the active credential (±1 time step) and, on success, issues a
`STEP_UP`-typed `auth_tokens` row (migration `040`) as a short-lived opaque proof, consumed exactly
once by `MfaService.consumeStepUpProof` — the same one-time-secret discipline every other opaque
credential in this module already follows.

**Not done**: this is the mechanism, not the wiring. `auth_sessions.assurance_level` and
`last_assurance_proof_at` are still written at `AAL1` only and never read; no endpoint calls
`consumeStepUpProof` yet, so nothing in this codebase currently *requires* a step-up proof before
proceeding — `UserAccountService.changePassword` is the natural first caller, since "prove you can
still authenticate before changing how you authenticate" is exactly what step-up is for, but
wiring it in was left out of this pass to avoid touching a working, already-tested workflow in the
same change that built the primitive it would depend on. `WEBAUTHN`/`RECOVERY_CODE` credential
types remain unenrolled, and TOTP has no recovery-code fallback for a lost authenticator.

## Organizations and co-host delegation — Built

`OrganizationService`, behind `POST /api/v1/organizations` and the
`/api/v1/organizations/{organizationId}/**` routes, is `organization_members`' first reader and
writer. Creating an organization makes the caller its first active `OWNER` and grants the
organization itself (as an `ORGANIZATION`-type `capability_grants` principal) the `HOST` role
bundle — the authority a delegation later narrows a subset of, realizing exactly the design
`CapabilityGrantService.revoke`'s cascade and `derived_from_grant_id` were built for: `delegate`
requires the requested capabilities to be a subset of the organization's own effective `HOST`
grant and the scope to narrow below `GLOBAL`, then issues the co-host's grant with
`derived_from_grant_id` pointing at that same organization grant, so revoking the organization's
own `HOST` authority cascades to every delegation drawn from it. `OWNER`/`ADMIN` membership is
checked inside the service rather than through a route-level `hasAuthority` rule, since it is a
fact about one organization, not a global capability. Removing the organization's last active
owner is rejected (`LAST_ACTIVE_OWNER`), enforced with
`OrganizationMemberRepository.countActiveOwnersForUpdate`'s row lock the same way schema comments
always intended.

Fixed a latent bug found while wiring this up: `OrganizationMember.userId`/`invitedBy` still named
the columns migration `037` renamed to `member_holder_id`/`invited_by_account_holder_id` once the
legacy `users` table those names referred to was retired — unnoticed only because nothing had used
the repository yet, the same class of mismatch `AuthAttempt.userId` had.

Out of scope for this pass: removing a member does not automatically revoke that member's own
delegated grants, so an operator removing a co-host should also revoke that co-host's delegations
explicitly through `DELETE /api/v1/organizations/{organizationId}/delegations/{grantId}`; and an
invitation has no notification — `invite` creates the `INVITED` row, but nothing emails or
otherwise tells the invitee it exists, unlike every other token-issuing flow in this module.
`AdminAccountService.resolveMarket` does not check `holderType`, so it already works unmodified for
an organization's own market resolution.

## Contact-channel management and phone verification — Built

`ContactChannelService`, behind `GET/POST /api/v1/users/me/contact-channels`,
`POST /api/v1/users/me/contact-channels/{channelId}/verification/{request,confirm}`, and
`DELETE /api/v1/users/me/contact-channels/{channelId}`, lets a holder add a channel of any type and
purpose, prove control of it with a 6-digit numeric code, list the current set, and remove a
non-primary one. Registration still only ever creates the `ACCOUNT`-purpose primary `EMAIL`
channel; every other channel now goes through this endpoint. Verification is deliberately not the
link-based flow `EmailVerificationService` uses for that primary channel: a code that must be typed
back works for both `PHONE` (SMS) and `EMAIL`, and a new `channel_id` column on `auth_tokens`
(migration `039`) lets the same `CONTACT_CHANNEL_VERIFICATION` token type name which of a holder's
several channels of one type a given code was issued for — something `EMAIL_VERIFICATION`'s
"resolve to the current primary channel" shortcut cannot express once a holder may register more
than one. Removing a channel sets a new `revoked_at` column rather than deleting the row, both
because a consumed verification token can still reference it by foreign key and because a removed
channel is evidence, not a mistake to erase; the primary channel for its type cannot be removed
through this endpoint, since promoting a replacement has no workflow yet. Phone delivery goes
through a new `platform.SmsSender` port, whose only implementation today
(`LoggingSmsSender`) logs the code instead of sending it — no SMS carrier account exists for this
codebase, so there is nothing to hold real credentials for yet. Both this event and
`EmailVerificationIssued`/`PasswordResetIssued` carry a raw secret, so
`ContactChannelVerificationNotifier` stays off Spring Modulith's event registry for the same reason
those two do; see [`../../architecture/event-publication-registry.md`](../../architecture/event-publication-registry.md).

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

## Market resolution — Built

`AdminAccountService.resolveMarket` (`PUT /api/v1/admin/users/{userId}/market`) records the market
an operator names for a holder, moving `context_state` to `RESOLVED` and unblocking
`AccountHolder.canTransact()`. It validates the code through `market`'s new public
`MarketLookup.findUsableByCode`, rejecting an unknown, draft, suspended, or retired code with `400
UNKNOWN_MARKET` rather than trusting the caller — no workflow infers a market from a currency, phone
number, or address. This is `identity`'s first live dependency on `market`; see
[`../../modules/identity.md`](../../modules/identity.md) and
[`../../modules/market.md`](../../modules/market.md). Every account holder still starts
`UNRESOLVED` at registration (see [`01-registration.md`](01-registration.md)), and
`AccountHolderRepository.findAllByContextStateOrderByCreatedAtAsc(UNRESOLVED)` is the query an
operator tool uses to find holders awaiting this call — the tool itself (a queue or worklist UI) is
still out of scope; only the resolving endpoint is built.

## Status

Administrator suspend/reactivate, session inventory and sign-out-everywhere, `auth_attempts`
velocity control, capability restrictions (single-capability), the identity audit trail, market
resolution, contact-channel management with phone verification, organizations with co-host
delegation, and TOTP enrollment with step-up proof issuance are **Built**. Everything else on this
page is still **Planned**: designed in D01, in some cases already scaffolded in the schema, but
with no live service or endpoint behind it — including wiring the built step-up mechanism into any
actual sensitive action.
