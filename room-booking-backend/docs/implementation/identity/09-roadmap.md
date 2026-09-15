# Roadmap

What is designed in
[`../../features/identity-accounts-and-access.md`](../../features/identity-accounts-and-access.md)
(D01) but not built. This page summarizes and links rather than restating that document's full
detail; read it directly for the complete rationale, the proposed-operations table, the revocation
matrix, error semantics, event contracts, and assurance-level table.

## Administrator suspend / reactivate

No controller sets an `account_holders.status` other than the one the account holder sets on
itself. `SecurityConfig` reserves `/api/v1/admin/**` behind `hasAuthority("ACCOUNT_SUSPEND")`, and
`RoleBundle.ADMIN` already carries `ACCOUNT_SUSPEND`/`ACCOUNT_REACTIVATE`, but no `ADMIN` grant is
ever issued and no endpoint exists to issue a suspension. See D01 § *Proposed operations* for the
target `PUT /api/v1/admin/accounts/{id}/status` shape.

## Session inventory and sign-out-everywhere

`auth_sessions` carries everything a "your devices" list and a self-service "sign out everywhere"
command need (`clientDescriptor`, `originHash`, `lastUsedAt`, live/revoked state), and
`RefreshTokenService.revokeAllSessionsForHolder` already implements the revocation half. No
controller exposes either a list or a targeted revoke.

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

## `auth_attempts` velocity control

The table, its check constraints, and its three velocity-lookup indexes exist; nothing in
`AuthenticationService`, `EmailVerificationService`, or `PasswordResetService` writes to it. There
is currently no login-attempt throttling or credential-stuffing defense beyond email verification's
own cooldown/quota.

## Capability restrictions in practice

`AuthorizationService.effectiveCapabilities` already subtracts active `capability_restrictions`
rows, and single-capability restrictions are fully wired. Nothing issues a restriction, and
capability-group restrictions (`capability_group` rather than `capability`) have no defined
group-to-capability taxonomy to expand against — see the note in
[`07-authorization.md`](07-authorization.md).

## Identity audit trail

D00's generic `audit_events` primitive (migration `012`) is the intended home for an append-only
identity audit trail; no identity workflow writes to it today. A status transition or token
revocation currently leaves no evidence beyond the mutated row itself.

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

Everything on this page is **Planned**: designed in D01, in some cases already scaffolded in the
schema, but with no live service or endpoint behind it.
