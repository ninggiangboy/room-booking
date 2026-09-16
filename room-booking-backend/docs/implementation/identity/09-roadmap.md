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

## Step-up, reauthentication, and multi-factor — TOTP enrollment, step-up proof, and password-change wiring built

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

`UserAccountService.changePassword` is now `consumeStepUpProof`'s first caller: when the holder has
an active TOTP credential, the `PUT /api/v1/users/me/password` request must carry the raw
`stepUpProof` `POST /api/v1/users/me/mfa/totp/step-up` returned, consumed with the same instant the
password rotation itself uses. A holder with no TOTP enrolled needs no proof — there is nothing to
step up with — so the existing current-password check alone still gates the change for them, matching
today's behavior. A missing proof is `401 STEP_UP_REQUIRED` (distinct from the wrong-proof
`401 INVALID_STEP_UP_PROOF`), so a client can tell "prompt for a code" apart from "the code was
wrong."

**Not done**: this is the second caller, not the whole mechanism. `auth_sessions.assurance_level`
and `last_assurance_proof_at` are still written at `AAL1` only and never read, so step-up here is
enforced per-action rather than through the session-level `AssuranceEvaluator` D01 describes; no
other sensitive action (organization ownership transfer, contact-channel removal, session
sign-out-everywhere) demands a proof yet. `WEBAUTHN`/`RECOVERY_CODE` credential types remain
unenrolled, and TOTP has no recovery-code fallback for a lost authenticator.

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

## Identity events through the outbox — Built (published, no external consumer yet)

`EmailVerificationIssued` and `PasswordResetIssued` are deliberately *not* on Spring Modulith's
`@ApplicationModuleListener`/outbox registry, for the reason recorded in
[`../../modules/identity.md`](../../modules/identity.md). D01 proposes durable, non-secret-bearing
identity facts (account created, session revoked, capability granted) published through that outbox
so other domains (`trust`, `admin`) can react without polling.

`AccountHolderCreated`, `CapabilityGranted`, and `SessionRevoked` (all in `dev.ngb.backend.identity`,
the module's public root package, since a public event's field types must themselves be consumable
by another module — `granteeType`/`source` on `CapabilityGranted` are therefore the underlying
enums' names rather than `identity.internal`'s `PrincipalType`/`GrantSource`) are now published, each
from the single existing writer of the fact it names: `AuthenticationService.registerUser`,
`CapabilityGrantService.issueRoleGrant`/`issueDelegatedGrant`, and
`RefreshTokenService`'s shared `revokeSessionAndTokens` (which every revocation path — self-service,
administrator suspension, and refresh-token reuse detection — already funneled through, so one
publish point covers all three).

Spring Modulith's `EventPublicationRegistry` only records a durable row per `(event, listener)`
pair — an event with no registered listener completes `ApplicationEventPublisher.publishEvent` and
leaves no trace, which would make "published through the outbox" true in name only. So this pass
also adds the first `@ApplicationModuleListener` in the codebase,
`internal.service.audit.IdentityFactAuditListener`, which turns each fact into an `identity.*`
`audit_events` row attributed to `ActorType.SYSTEM` (the listener runs asynchronously, after
commit, with no access to the original caller's principal). This does not replace the
higher-fidelity, actor-attributed `account.*`/`session.*`/`organization.*` rows `AdminAccountService`,
`UserController`, and `OrganizationService` already write inline in the same transaction as the
command that caused them — `identity.capability_granted` and `identity.session_revoked` coexist with
those, deliberately, as a second and coarser trail. Registration and `HostOnboardingService`'s role
grants had no audit coverage at all before this; they do now.

**Not done**: no `trust` or `admin` listener exists yet to react to any of the three events — that
is the actual cross-module reactivity D01 describes, and this pass only builds the durable primitive
those modules would listen to. `EmailVerificationIssued`/`PasswordResetIssued` staying off the
registry is unchanged and remains the deliberate, documented exception it always was.

## Erasure — `DELETION_REQUESTED` state built; obligation checks and anonymization still Planned

`AccountHolderStatus` gained `DELETION_REQUESTED` (migration `042`, widening
`ck_account_holders_status` again). `UserAccountService.deleteOwnAccount` (`DELETE
/api/v1/users/me`) now moves a holder there instead of directly to `CLOSED`, revoking every session
and unconsumed opaque token in the same transaction exactly as before — matching D01's lifecycle
table, `AccountHolderFinder`'s active-check excludes this state the same way it excludes
`SUSPENDED`, so a holder who requested deletion cannot authenticate again. A new operator command,
`AdminAccountService.completeDeletion` (`POST /api/v1/admin/users/{userId}/complete-deletion`),
transitions `DELETION_REQUESTED` to `CLOSED`; `AdminAccountService.updateStatus`'s existing
`SUPPORTED_STATUSES` check (`ACTIVE`/`SUSPENDED` only) already keeps that coarser endpoint from
touching either state, so this is the only path in or out.

**Not done, deliberately**: this pass adds the state transition only, not the workflow D01 actually
describes. `completeDeletion` performs the status change unconditionally — it does not check future
confirmed stays, unsettled balances, or open cases before allowing completion (D01's own question 11:
what obligations block completion, and how long the window is, are unresolved), does not check a
legal hold (D22, not built), and does not anonymize or erase any personal data — the row and its
personal data remain exactly as before, identical to what immediate `CLOSED` did previously. Building
those requires: an owner for each obligation check (`booking`, `ledger`, `trust`'s cases), D22's
legal-hold primitive, a decision on what is erased versus pseudonymized and in which stores (D01's
question 12), and the `AccountDeletionRequested`/`AccountErased` events other data holders would
acknowledge propagation against. None of that exists yet, so `completeDeletion` today is an operator
manually vouching that it is safe to close the account, not an enforced workflow.

## `PENDING_VERIFICATION` state — Built

`AccountHolderStatus` gained `PENDING_VERIFICATION` (migration `041`, which widens
`ck_account_holders_status`; the column default stays `'ACTIVE'`, so nothing but
`UserRegistrationFactory` sets the new value). `UserRegistrationFactory.create` now starts a
self-registered person there instead of `ACTIVE`, since the primary email channel registration
creates is unproven until `EmailVerificationService.verify` confirms it — which is also the only
place that ever moves a holder out of the state, transitioning it to `ACTIVE` in the same
transaction as marking the channel verified. `OrganizationFactory` is unchanged and still creates
an organization `ACTIVE`: an organization has no email of its own to verify, and its owner is
already a verified person by the time they create one.

Matching the design table's "Permitted" authentication entry, `AccountHolderFinder`'s shared
active-check and `IdentityFacts.resolve` (the JWT filter's per-request reload) both treat
`PENDING_VERIFICATION` the same as `ACTIVE` — a holder in this state can still log in and use every
endpoint gated only by "does this account exist and work." `PasswordResetService.resetPassword`'s
inline status filter was widened the same way, so a holder who forgets their password before
verifying is not locked out of recovery. `AccountHolder.canTransact()` was deliberately left
requiring `ACTIVE` exactly — it is the one place D01's "reduced capabilities" already has a concrete,
existing meaning: an unverified holder cannot book or list, the same restriction an unresolved
market already imposes.

**Not done**: which capabilities beyond `canTransact()` a `PENDING_VERIFICATION` holder should lose
remains the open product decision D01 itself flags (its question 10). Today the state changes
nothing else — a holder can change their password, enroll MFA, manage sessions and contact channels,
and grant themselves the host capability before ever verifying their email, identical to `ACTIVE`.
`AdminAccountService.updateStatus` still only supports the `ACTIVE`/`SUSPENDED` pair the design's
lifecycle diagram shows (`PENDING_VERIFICATION -> ACTIVE` only), so an operator cannot suspend an
account still in this state.

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
delegation, TOTP enrollment with step-up proof issuance and password-change wiring, identity events
through the outbox (published, durably recorded via a first identity-internal listener, but with no
`trust`/`admin` consumer yet), the `PENDING_VERIFICATION` account state, and the `DELETION_REQUESTED`
account state (transition only — see "Erasure" above) are **Built**. Everything else on this page is
still **Planned**: designed in D01, in some cases already scaffolded in the schema, but with no live
service or endpoint behind it — including wiring step-up into any other sensitive action, the
session-level `AssuranceEvaluator` that would enforce it generally instead of per-action, any
`trust`/`admin` reaction to the three events now published, which capabilities a
`PENDING_VERIFICATION` holder should lose beyond `canTransact()`, and every obligation check,
legal-hold check, and anonymization step `completeDeletion` does not yet perform.
