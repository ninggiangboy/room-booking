# Identity, accounts, and access

## Purpose

This document defines the target design for accounts, credentials, authentication, sessions,
organizations, delegated permissions, and authorization evidence. It expands
[D01 — Identity, accounts, and access](../marketplace-problem-breakdown.md#d01--identity-accounts-and-access)
from the marketplace master map into an implementation-oriented design.

The central question is:

> How does the platform prove which person, organization, operator, workload, or provider is acting,
> decide what that actor may currently do to a specific resource, revoke that ability immediately
> when trust changes, and let a legitimate owner recover access without giving an attacker the same
> path?

D01 owns the actor and the actor's current authority. It does not own the business rule that consumes
them:

- [platform foundation](platform-foundation.md) owns the `ActorContext` shape, request/correlation
  propagation, command idempotency, outbox/inbox, audit envelope, and error contract that D01
  populates and uses;
- [trust, safety, fraud, and content moderation](trust-safety-fraud-and-moderation.md) owns risk
  scoring, challenge/restriction policy, account-takeover (ATO) review, fraud labels, and appeals;
  D01 executes the identity-side effects those decisions request;
- [disputes, damage claims, insurance, and customer support](disputes-damage-claims-and-support.md)
  owns support cases, agent queues, monetary limits, and purpose-bound case access; D01 supplies the
  operator principal, scope, and step-up evidence those controls bind to;
- [messaging, notifications, and stay operations](messaging-notifications-and-stay-operations.md)
  owns conversation membership, notification intent, consent records, and delivery; D01 supplies the
  verified contact channel and the participant's current authority;
- [reviews, aspect intelligence, and reputation](review-reputation-and-aspect-intelligence.md) owns
  review rights and public attribution; D01 owns only the account facts that attribution reads;
- [Vietnam market readiness](multi-market-compliance-and-localization.md) owns market activation,
  legal entity, locale/currency policy, lawful basis, and data-residency constraints applied to
  identity data;
- host verification, know-your-customer/know-your-business (KYC/KYB) screening, licensing, tax
  identity, and payout eligibility belong to D02 host onboarding and compliance, which has no
  authoritative focused design yet. D01 stores the resulting capability grant, never the compliance
  judgement itself.

Identity is consumed by every other domain, so its contract must stay small, explicit, and stable:
an authenticated principal, a current account state, a set of resource-scoped capabilities, an
assurance level for this session, and append-only evidence of how each of those changed.

## Status and dependencies

This is a target design. The repository implements a working subset of authentication and account
management, not the complete D01 contract.

Current foundations are:

- [`001-identity.sql`](../../src/main/resources/db/changelog/changes/001-identity.sql) creates
  `users` (case-insensitive unique email, optional unique phone number, password hash, display name,
  avatar, `ACTIVE`/`SUSPENDED`/`DELETED` status, email/phone verification instants, optimistic
  `version`), `user_roles` with a composite primary key over `GUEST`/`HOST`/`ADMIN`, and
  `host_profiles` keyed by `user_id` with an `identity_status` of
  `UNVERIFIED`/`PENDING`/`VERIFIED`/`REJECTED`;
- [`007-email-verification.sql`](../../src/main/resources/db/changelog/changes/007-email-verification.sql),
  [`008-auth-tokens.sql`](../../src/main/resources/db/changelog/changes/008-auth-tokens.sql), and
  [`009-password-reset.sql`](../../src/main/resources/db/changelog/changes/009-password-reset.sql)
  produce one `auth_tokens` table holding a unique Secure Hash Algorithm 256 (SHA-256) token digest,
  a `type` of `EMAIL_VERIFICATION`/`PASSWORD_RESET`/`REFRESH_TOKEN`, an expiry, and a consumption
  instant, with indexes for per-user lookup and unconsumed-token expiry;
- [`AuthController`](../../src/main/java/dev/ngb/backend/controller/AuthController.java) exposes
  register, login, refresh, logout, forgot/reset password, and email-verification request/confirm;
- [`UserController`](../../src/main/java/dev/ngb/backend/controller/UserController.java) exposes the
  current-user projection, a public email-existence check, password change, atomic host onboarding,
  and self-service soft deletion;
- [`SecurityConfig`](../../src/main/java/dev/ngb/backend/config/SecurityConfig.java) is stateless,
  disables Cross-Site Request Forgery (CSRF) protection for a bearer-token API, lists public routes
  explicitly, reserves `/api/v1/admin/**` for the `ADMIN` role, and requires authentication
  everywhere else;
- [`JwtAuthenticationFilter`](../../src/main/java/dev/ngb/backend/filter/JwtAuthenticationFilter.java)
  verifies the JSON Web Token (JWT) signature and expiry, then reloads account status and roles from
  PostgreSQL on every protected request, so a role grant applies on the next request and a
  suspension or deletion blocks an unexpired access token immediately;
- opaque refresh, verification, and reset secrets are returned once and stored only as digests;
  refreshing consumes the presented token and issues a new one; password reset and account status
  changes revoke outstanding unconsumed tokens;
- [`PasswordPolicy`](../../src/main/java/dev/ngb/backend/service/validation/PasswordPolicy.java)
  enforces length, character classes, and the 72-byte BCrypt input limit through Spring Security's
  delegating password encoder;
- email verification applies a per-account cooldown plus a rolling-window request limit, and
  password recovery returns the same `204 No Content` for registered and unregistered addresses.

These are useful foundations, not proof of complete identity behavior. The repository does not yet
contain:

- an organization or account-holder concept; every principal is one natural-person row;
- resource-scoped authorization. `user_roles` answers "has the `HOST` role", never "may act on this
  listing"; no co-host, delegated, or team permission exists;
- a session or device record. A refresh token is the only session-like row, and it carries no device,
  network, assurance, or last-used evidence;
- refresh-token reuse detection, session inventory, per-session revocation, or a "sign out everywhere"
  command;
- authentication assurance levels, step-up authentication, reauthentication before a security change,
  multi-factor enrolment, or a security-change cooling-off period;
- a phone-verification workflow. `users.phone_number` and `users.phone_verified_at` exist but nothing
  writes the verification instant;
- login attempt records, credential-stuffing controls, breached-credential checks, or per-account
  lockout; only email-verification requests are rate limited;
- an append-only identity audit trail. Status transitions and token revocations leave no evidence
  record beyond the mutated row;
- an account-recovery path that survives an attacker-controlled contact channel;
- scoped-restriction state distinct from the coarse `SUSPENDED` account status;
- erasure or anonymization behavior for `DELETED` accounts; the row and its personal data remain;
- domain events for identity facts. Authentication email is delivered by an in-process
  `ApplicationEventPublisher` listener after commit, which is best-effort and may be lost on
  process failure;
- any automated test. `src/test` does not exist.

One documentation discrepancy is material to this design and is recorded here rather than silently
inherited: the root `README.md` API table, `GUIDE.md` section 9, and
[`../data-model/001-identity.md`](../data-model/001-identity.md) all describe
`PUT /api/v1/admin/users/{userId}/status` as an available administrator operation. No controller,
service method, or repository call implements it; `UpdateUserStatusRequest` is an unreferenced
data-transfer object and `SecurityConfig` only reserves the path prefix. Administrator-driven
suspension and reactivation are therefore a **target capability**, not an implemented one, and this
document treats them as such.

D01 depends on D00 primitives and on approved market and privacy policy. Recommended dependency
order:

1. Approve the principal model (person versus organization), assurance levels, capability catalog,
   session lifetime, recovery policy, retention classes, and operator-authority boundary.
2. Add the identity audit record, login-attempt evidence, and session/device rows through forward
   migrations, and start writing them from the existing flows without changing response contracts.
3. Introduce resource-scoped capability evaluation behind the existing role checks, then make it
   authoritative once every consumer reads it.
4. Add organizations, membership, and delegated grants once capability evaluation is authoritative.
5. Add assurance levels, step-up, security-change cooldown, and account-recovery review.
6. Publish identity events through the D00 outbox and let D15 request scoped restrictions and session
   revocation through supported commands.
7. Implement erasure, anonymization, and legal-hold behavior with D22.

No Java code, configuration, or migration is changed by this document.

## Goals

- Give every acting principal — natural person, organization, operator, workload, and provider — a
  stable identity, an explicit authentication method, and a documented authority boundary.
- Make "may this actor perform this action on this resource right now" one server-side decision with
  recorded inputs, never a client assertion or a bare role string.
- Support one person holding several capacities (guest, host, co-host of another host's portfolio,
  organization member, operator) without duplicate accounts or merged authority.
- Make sessions enumerable, individually revocable, and immediately ineffective when an account, a
  grant, or a credential changes.
- Prove authentication strength per session so consequential actions can demand a stronger proof
  instead of trusting an old login.
- Give a legitimate owner a recovery path that an attacker holding one compromised channel cannot
  complete.
- Keep every credential and recovery secret a non-recoverable verifier.
- Produce append-only evidence for every security-relevant change so support, risk, and audit can
  reconstruct who changed what, from where, under which authority.
- Let risk and compliance domains restrict capabilities precisely and temporarily without inventing
  their own account states.
- Honour deletion and erasure obligations while preserving the minimum contractual and financial
  history other domains require.
- Expose identity facts to other domains as a small, versioned, cache-safe contract that never
  becomes their policy engine.

## Non-goals

- Deciding host eligibility, KYC/KYB outcome, sanctions results, licence validity, tax identity, or
  payout readiness. D02 owns those decisions; D01 stores only the resulting capability grant and its
  provenance.
- Scoring risk, choosing an intervention, or adjudicating an appeal.
  [Trust and safety](trust-safety-fraud-and-moderation.md) owns them.
- Owning support cases, agent queues, monetary approval limits, or case-scoped evidence access.
  [Disputes and support](disputes-damage-claims-and-support.md) owns them.
- Owning notification preferences, consent records, templates, or delivery.
  [Messaging and notifications](messaging-notifications-and-stay-operations.md) owns them; D01 owns
  only whether a channel is verified and reachable.
- Owning public profile presentation, reputation, or review attribution rules.
- Owning market activation, lawful basis, residency, or retention policy text. D01 implements the
  approved rules from the [market contract](multi-market-compliance-and-localization.md) and D22.
- Building a general-purpose identity provider, single sign-on product, policy engine, or customer
  data platform.
- Storing identity documents, biometric templates, payment credentials, or payout destinations. Those
  live behind the compliance and finance boundaries with their own access and retention controls.
- Treating an access token claim, a cached permission, an analytics profile, or a device fingerprint
  as an authorization source of truth.
- Using a large language model (LLM) to authenticate a person, grant or revoke a capability, approve
  a recovery, judge an appeal, or decide that an account is compromised.

## Core principles and invariants

### Authentication is a proof at an instant, not a standing trust

A successful login proves possession of a credential at one moment through one method. It does not
prove continued control, does not survive a credential change, and does not extend to an action whose
policy demands stronger or fresher proof. Every session records how and when it was authenticated;
consequential commands compare that evidence against the action's requirement rather than assuming a
valid bearer token is sufficient.

### Role is a coarse hint; authority is resource-scoped

Holding the `HOST` role means the account may act as a host somewhere. It never means the account may
act on a specific property, listing, booking, conversation, payout destination, or case. Every
protected command resolves the actor against the concrete resource, the owning organization, the
market, the current account state, and the requested action. A role check that is not followed by a
resource check is an incomplete authorization.

### Delegated authority is bounded, derived, and revocable

A grant issued by a delegator can never exceed the delegator's own current authority, outlive its
declared expiry, or survive the delegator losing that authority. Delegation is explicit data with
grantor, grantee, scope, capability set, effective interval, reason, and audit evidence — never an
extra role on an account. Removing a grant stops future access immediately and never rewrites who
previously acted under it.

### Account state decides capability; it is not a display field

`ACTIVE`, `SUSPENDED`, and `DELETED` are inputs to a capability decision that also considers scoped
restrictions, verification state, market eligibility, and assurance. A consumer must ask identity for
an effective capability answer; reading a status column and interpreting it locally recreates policy
in every domain and drifts.

### Credentials and recovery secrets are verifiers, never retrievable values

Passwords are stored only as adaptive hashes. Opaque refresh, verification, reset, and invitation
secrets are returned to their recipient exactly once and stored only as digests. No support tool,
administrative screen, log, event, error body, metric label, or backup export may reveal a usable
secret. A workflow that requires reading a secret back is redesigned, not exempted.

### A session is a durable, revocable fact

Every authenticated session is a row with identity, principal, creation evidence, authentication
method, assurance level, expiry, and revocation state. Revoking it takes effect for refresh
immediately and for access tokens within the documented bounded window. "The token has not expired
yet" is never a reason to keep serving a revoked session.

### One verified channel proves one fact at one time

Verifying an email address proves control of that address at the verification instant. It is not
proof of identity, not permanent, and not transferable to a changed address. Changing a verified
channel restarts verification, notifies the previous channel, and applies the approved cooling-off
before high-impact capabilities relying on that channel resume.

### Enumeration resistance is a contract, not a courtesy

Registration, login, recovery, invitation, and existence-check responses must not let an unauthorized
caller learn whether an account exists, is suspended, holds a role, or belongs to an organization,
beyond what approved policy allows. Response bodies, status codes, error codes, and observable timing
follow the same disclosure class.

### Security changes are consequential actions

Password change, contact change, multi-factor enrolment or removal, delegation grant or revocation,
role change, organization ownership transfer, status change, and account deletion each require
authorization, appropriate assurance, idempotent execution, notification to the account owner, and
append-only evidence. A silent security change is a defect regardless of who performed it.

### Identity is evidence for other domains, never their policy

D01 answers who is acting and what they may do. It does not decide whether a booking may be
cancelled, a payout released, a review published, or a case closed. Consumers receive verified facts
and apply their own rules; identity does not accumulate their business logic to make integration
convenient.

### Erasure removes personal data; it does not remove history

Contractual, financial, safety, and audit history survives account deletion. Erasure removes or
irreversibly transforms personal content and replaces attribution with a stable pseudonymous
reference, preserving the minimum integrity evidence permitted by approved policy. A deleted account
cannot authenticate, cannot be reactivated, and cannot be recovered by a support action.

## Domain vocabulary

| Term | Definition |
| --- | --- |
| Principal | Any entity that can act: person account, organization, operator, workload, or provider |
| Person account | One natural person's platform account, identified by a stable UUID |
| Organization | A non-person account holder that owns supply, contracts, and members |
| Membership | A person's association with an organization, carrying a member role and status |
| Capability | A named, checkable permission such as `LISTING_PUBLISH` or `PAYOUT_DESTINATION_CHANGE` |
| Scope | The resource boundary a capability applies to: global, market, organization, portfolio, listing, booking, or case |
| Grant | A record binding a principal to capabilities in a scope for an effective interval |
| Delegation | A grant issued by one principal to another and bounded by the grantor's own authority |
| Role | A named bundle of capabilities used for readability; expanded to capabilities at decision time |
| Credential | A verifier for an authentication method: password hash, second-factor secret, or provider binding |
| Authentication method | The mechanism that produced a proof: password, one-time code, second factor, recovery, or provider |
| Assurance level | Recorded strength/freshness of a session's authentication, compared against action requirements |
| Session | A durable authenticated context with its own identity, evidence, expiry, and revocation state |
| Access token | Short-lived signed bearer credential naming the principal and session; not authoritative for authority |
| Refresh token | Rotating opaque secret bound to one session; presenting a consumed value is reuse |
| Reuse detection | Recognizing a consumed refresh secret and treating the session lineage as compromised |
| Step-up | Requiring an additional or fresher proof before one consequential action |
| Reauthentication | Requiring the current credential again for a security-affecting command |
| Cooling-off | An enforced delay after a security change before dependent high-impact capabilities resume |
| Verified contact | An email address or phone number whose control was proven at a recorded instant |
| Account status | Coarse platform-wide lifecycle: `ACTIVE`, `SUSPENDED`, `DELETED` |
| Scoped restriction | A bounded, reasoned capability removal requested by risk or compliance |
| Identity audit event | Append-only evidence of a security-relevant identity change and its authority |
| Pseudonymous reference | Stable non-personal identifier retained after erasure so history stays joinable |

## Ownership and source-of-truth matrix

| Fact or decision | Authoritative owner | D01 responsibility | Never authoritative |
| --- | --- | --- | --- |
| Principal existence and identity | D01 | Stable UUID, lifecycle, uniqueness | Email string, provider subject, display name |
| Credential validity | D01 | Verify against stored verifier | Client claim, cached result, prior session |
| Session existence and revocation | D01 | Session row, expiry, revocation state | Unexpired access token alone |
| Authentication assurance | D01 | Recorded method/instant and comparison rule | Client-declared factor, device claim |
| Contact-channel verification | D01 | Proof, instant, and change protection | Provider bounce data, user assertion |
| Capability grants and scope | D01 plus granting domain | Store, evaluate, expire, revoke, audit | Role name alone, JWT claim, cached list |
| Host capability eligibility | D02 compliance policy | Record the granted capability and provenance | Verification-provider response, `identity_status` alone |
| Risk restriction on an account | D15 decision | Apply and expire the scoped restriction | Identity's own inference from behavior |
| Support/operator authority | D16 and D21 policy | Authenticate the operator and enforce declared scope | Being an internal caller, `ADMIN` role alone |
| Account status transition | D01 command under D15/D21/D22 policy | Execute transition, revoke sessions, audit | Direct database edit, provider webhook |
| Notification consent and preferences | D12 | Supply verified reachable channel | Verified flag treated as consent |
| Personal-data retention and erasure | D22 policy | Execute approved transformation, keep integrity evidence | Domain-local ad hoc deletion |
| Public profile and reputation | D03 and D14 | Supply account facts they attribute | Identity-owned trust score |
| Actor context propagation shape | D00 | Populate verified actor and authority | Request body actor, forwarded role header |

Actor types are guest, host, co-host or organization member, operator (support, finance, risk,
content, admin), authenticated workload, and verified provider. Each has a separate authentication
boundary and a least-privilege capability set. `SYSTEM` is a named workload with declared
capabilities and causation, never an anonymous superuser. An operator holds only the capabilities
their current assignment, scope, limit, and approval permit; being internal grants nothing.

## End-to-end flow

```text
Registration / invitation
  -> normalize and validate contact identity
  -> create person account (or accept organization invitation)
  -> issue verification proof for the contact channel
  -> grant baseline guest capabilities

Authentication
  -> resolve principal by normalized identifier
  -> evaluate account status, restrictions, and attempt velocity
  -> verify credential; optionally require second factor
  -> create session with method, assurance, and evidence
  -> issue access token (short) and refresh secret (rotating)

Authorized command
  -> verify access token signature/expiry
  -> load session; reject revoked/expired session
  -> load principal status and effective grants for the target scope
  -> compare required capability and required assurance
  -> owning domain executes its own business rule
  -> append identity audit evidence when the action is security relevant

Session continuation
  -> present refresh secret
  -> consume it atomically and issue the successor
  -> a consumed secret means reuse: revoke the lineage and raise a risk signal

Security change / recovery
  -> require current credential or a stronger proof
  -> apply cooling-off where policy demands
  -> execute change, revoke dependent sessions and pending proofs
  -> notify the previous verified channel
  -> append audit evidence and publish the committed identity fact
```

The atomic boundary is one local transaction per identity command: state change, session or token
effect, idempotency result, audit evidence, and outbox facts commit together. Notification delivery,
risk evaluation, and downstream projections are asynchronous consequences and never extend the
transaction.

## Account and principal model

### Person accounts and organizations

A person account represents one natural person and is the only principal that can authenticate with
a password or second factor. An organization is a non-person account holder that owns properties,
listings, contracts, statements, and payout destinations, and that acts only through members.

Required properties of the split:

- an organization has at least one member holding the owner capability at all times; the last owner
  cannot be removed, only replaced through an audited transfer;
- a person may belong to several organizations with different member roles, and may also hold
  personal supply; the two authorities never merge implicitly;
- resources are owned by an account holder — a person or an organization — and authorization always
  resolves through that holder, so converting a personal portfolio into an organization portfolio is
  an ownership transfer rather than a permission hack;
- compliance and payout facts attach to the account holder, because the seller of the accommodation
  is the holder, not the member operating the calendar today.

Whether an organization is required for business hosts in Vietnam, and whether one person may hold
several host organizations, are open product decisions recorded below. The contract must permit both
without a breaking change.

### Account lifecycle states

```text
PENDING_VERIFICATION -> ACTIVE
ACTIVE               -> SUSPENDED -> ACTIVE
ACTIVE | SUSPENDED   -> DELETION_REQUESTED -> DELETED
DELETED              -> (terminal)
```

| State | Meaning | Authentication | Notes |
| --- | --- | --- | --- |
| `PENDING_VERIFICATION` | Created, primary channel unproven | Permitted with reduced capabilities | Optional; current code registers directly as active |
| `ACTIVE` | Normal account | Permitted | Scoped restrictions may still apply |
| `SUSPENDED` | Policy-approved account-wide incapacity | Denied | Reversible; requires reason, actor, and evidence |
| `DELETION_REQUESTED` | Owner asked to delete; obligations pending | Denied for new sessions | Bounded window for cancellation and settlement |
| `DELETED` | Terminal | Denied permanently | Personal data erased or transformed per policy |

Rules:

- suspension and deletion revoke every session and every unconsumed proof in the same transaction as
  the status change;
- `SUSPENDED` is reserved for account-wide incapacity approved by policy; anything narrower uses a
  scoped restriction so a guest with a payment-risk flag does not lose the ability to read an
  existing booking or contact support;
- `DELETION_REQUESTED` exists because a host with future confirmed stays, unsettled balances, or open
  cases cannot become unreachable instantly; the window, the obligations that block completion, and
  who may cancel the request are product decisions;
- `DELETED` is terminal. No administrative command restores it. A returning person registers a new
  account, and the historical pseudonymous reference is not reattached.

The repository currently registers accounts directly as `ACTIVE`, has no
`PENDING_VERIFICATION`/`DELETION_REQUESTED` state, and implements soft deletion as an immediate
terminal `DELETED`. Adding the intermediate states requires a forward migration that widens the check
constraint and a compatible reader deployment.

### Contact channels

A contact channel is an email address or phone number with a normalized form, a purpose, a
verification instant, and a primacy flag. Requirements:

- normalization happens once at the boundary; the original spelling is retained only where evidence
  or presentation requires it, and `citext` uniqueness remains the final defense for email;
- uniqueness is enforced on the normalized form of a verified primary channel; unverified duplicates
  must not let one person block another person's address indefinitely, so unverified claims expire;
- changing a verified channel creates a new unverified claim, keeps the old channel active until the
  new one is proven, notifies the old channel, and applies the approved cooling-off before recovery
  or payout-adjacent capabilities use the new channel;
- phone verification is a required target capability for markets that use short-message service (SMS)
  in recovery or notification; the existing `phone_verified_at` column has no writer today;
- a channel used for recovery must be verified. An unverified channel may receive only the proof that
  verifies it.

### Profile facts and their consumers

D01 owns display name, avatar reference, preferred locale and time zone, and verified contact state.
It does not own host biography, listing content, reputation aggregates, or private notes.
`host_profiles.average_rating` and `review_count` are read-model counters whose source of truth is
qualified review publication as defined in
[reviews and reputation](review-reputation-and-aspect-intelligence.md); identity must not treat them
as trust inputs, and must never derive a generic human trust score.

## Credential and authentication design

### Credential types

| Credential | Storage | Rotation and revocation |
| --- | --- | --- |
| Password | Adaptive hash through the delegating encoder, with the encoder identifier retained | Changed by owner or recovery; change revokes dependent sessions |
| Time-based one-time password (TOTP) secret | Encrypted at rest with a versioned key; never displayed after enrolment | Removal requires reauthentication and notification |
| Recovery codes | One-way digests, single use, counted | Regenerating invalidates the previous set |
| Email or SMS one-time code | Short-lived digest with attempt counter | Consumed on use; expires; limited attempts |
| Provider binding (if adopted) | Provider issuer plus subject, unique per issuer | Unlinking requires another usable credential to remain |
| Workload credential | Managed secret or workload identity through D00's secret boundary | Rotated on schedule and on revocation |

A person account must always retain at least one usable authentication path. Removing the last one is
rejected rather than silently locking the owner out.

Password rules keep the current policy — minimum length, mixed character classes, and the 72-byte
BCrypt input limit — and add, subject to approval, a breached-credential check against an approved
service or offline list, with a documented failure-open or failure-closed behavior when that check is
unavailable.

### Authentication assurance

Each session records the method that created it and an assurance level:

| Level | Typical proof | Example permitted actions |
| --- | --- | --- |
| `L1` | Valid session from a single-factor login | Browse, search, read own bookings |
| `L2` | Fresh single-factor proof within the freshness window, or verified-channel code | Create a booking, message a host, change display name |
| `L3` | Second factor or reauthentication within a short freshness window | Change password or contact, manage delegation, change payout destination, execute operator commands |

Requirements:

- the required level is a property of the action, declared by the owning domain and enforced when the
  command executes, not only in the user interface;
- freshness is evaluated against the recorded proof instant using the shared clock; an old `L3` proof
  decays to `L2` and then to `L1` rather than persisting for the life of the session;
- an assurance upgrade is bound to the session that requested it and is not transferable;
- a step-up failure is a distinct outcome from an authorization failure so clients can prompt
  correctly and risk can measure it.

Which actions require `L3`, and whether multi-factor authentication is mandatory for operator and
host-payout principals, are open decisions. Making it mandatory for operators is the recommended
default.

### Login evaluation

```text
1. Normalize the identifier and resolve the principal (constant-work path when absent).
2. Load account status and active restrictions.
3. Evaluate attempt velocity by account, source, and device fingerprint.
4. Request a risk decision where policy requires one; a timeout falls back to the deterministic rule.
5. Verify the credential with a constant-time comparison.
6. Require a second factor when enrolled or when policy or risk demands it.
7. Create the session and issue the token pair.
8. Record the attempt outcome and append audit evidence.
```

Rules:

- an unknown identifier and a wrong password return the same `INVALID_CREDENTIALS` outcome with
  comparable work, so absence is not observable through response or timing;
- a suspended or deleted account also returns a non-disclosing outcome to an unauthenticated caller;
  the owner learns the state through a channel that proves ownership;
- attempt records are retained with a hashed or truncated source address under the approved privacy
  policy, never as raw indefinitely retained personal data;
- lockout is bounded, self-clearing, and cannot be used by a third party to deny a victim access
  indefinitely; progressive delay plus challenge is preferred over a hard lock;
- a successful login after a run of failures is itself a risk signal published to D15.

### Step-up, reauthentication, and cooling-off

- Reauthentication requires the current password or an equivalent proof for password change, contact
  change, factor management, delegation management, and organization ownership transfer.
- Step-up requires a second factor for the `L3` actions listed above.
- A cooling-off period follows a password reset performed through recovery, a primary-contact change,
  and a second-factor removal. During cooling-off, payout-destination changes and other configured
  high-impact capabilities are unavailable, and the previous verified channel has been notified with
  a report path.
- Cooling-off durations, and whether a support agent may waive one, are open decisions; a waiver, if
  allowed, must be an audited maker-checker action with a reason.

### Abuse and enumeration controls

- Registration, login, recovery, verification resend, invitation acceptance, and the email-existence
  check are rate limited per account, per source, and globally, with bounded and observable counters.
- The public `GET /api/v1/users/email-exists` endpoint is an intentional enumeration surface. Keeping
  it requires an explicit product decision, aggressive rate limiting, and a documented acceptance of
  the disclosure; replacing it with an authenticated or challenge-protected check is the recommended
  alternative.
- Bulk invitation, member listing, and organization search are authorized and bounded so directory
  data cannot be harvested.
- Automated-traffic controls belong to D15 and D23; identity supplies the signals and honours the
  returned decision.

## Session and token design

### Session model

A session is a durable record with its own UUID and:

- principal identity and, where relevant, the active organization context;
- creation instant, authentication method, assurance level, and last assurance-proof instant;
- device and client descriptor, hashed or truncated network origin, and last-used instant;
- absolute expiry and idle expiry;
- revocation state, revocation reason, revoking actor, and revocation instant;
- the identity of the current refresh secret and the session's rotation generation.

Sessions are enumerable by their owner and by an authorized operator acting under a case. Listing a
session never reveals a secret.

### Access token

The access token stays a short-lived signed JWT. Target requirements:

- claims name the principal (`sub`), the session identity, the issued and expiry instants, the token
  identity, the issuer, and the audience;
- role and capability claims, if retained, are a client convenience explicitly documented as a
  snapshot; the server never authorizes from them. The current filter already reloads status and roles
  from PostgreSQL per request, and that behavior is required, not optional;
- session revocation must take effect within a documented bounded window. Because the token is
  stateless, either the access-token lifetime is short enough that the window is acceptable, or the
  request path consults a revocation check. The current design reloads the account per request but
  does not consult a session record, so revoking one session while leaving the account active is
  currently impossible;
- signing keys are versioned through D00's secret boundary, support overlapping verification during
  rotation, and single-current signing;
- tokens are never placed in URLs, logs, events, error bodies, or metric labels.

### Refresh rotation and reuse detection

Each refresh presentation consumes the presented secret and issues exactly one successor inside one
transaction. The database uniqueness of the token digest plus a conditional update on the unconsumed
state is the final defense against two concurrent refreshes both succeeding.

Presenting an already-consumed secret is **reuse**, and the target behavior is:

1. reject the request;
2. revoke the whole session lineage — the session and any successor tokens derived from it;
3. record an identity audit event and publish a risk signal to D15;
4. notify the account owner through a verified channel;
5. never return information that helps the presenter learn whether the lineage was live.

The repository currently rejects a consumed token but performs no lineage revocation, no signal, and
no notification, so a stolen-then-rotated token remains usable by whichever party refreshed last.
Reuse detection is a required target capability.

Additional rules:

- absolute session lifetime is bounded regardless of continued rotation, so an indefinitely refreshed
  session eventually requires a new authentication;
- logout revokes the presented session; "sign out everywhere" revokes all sessions for the principal
  and is available to the owner without support involvement;
- password change, password reset, contact change, factor removal, suspension, and deletion revoke
  sessions according to a documented matrix rather than ad hoc per-flow behavior.

### Revocation matrix

| Trigger | Sessions revoked | Pending proofs revoked | Notification |
| --- | --- | --- | --- |
| Owner logout | The presented session | None | No |
| Owner "sign out everywhere" | All | None | Yes |
| Password change (authenticated) | All except optionally the acting session | Reset tokens | Yes |
| Password reset (recovery) | All | Reset and verification tokens | Yes |
| Primary contact change | All | Verification tokens for the old channel | Both channels |
| Second-factor removal | All | Enrolment proofs | Yes |
| Refresh reuse detected | Lineage of the reused token | Its successors | Yes |
| Risk-requested revocation (D15) | As requested by scope | As requested | Per risk policy |
| Suspension | All | All | Per policy |
| Deletion | All | All | Yes, before erasure |

## Account recovery

Recovery is the highest-risk identity workflow because it must work for the owner who has lost every
credential and fail for the attacker who has obtained one channel.

Target design:

- **Password reset by verified channel.** The current behavior is kept: a single-use short-lived
  opaque token delivered to the verified address, an identical `204 No Content` response for known
  and unknown addresses, consumption on use, and revocation of outstanding tokens on success. Target
  additions are session revocation, cooling-off, notification to the account owner, and audit
  evidence.
- **Recovery when the channel itself is disputed.** If the primary channel changed recently, if the
  request originates from an unrecognized context, or if risk requires it, the automated path is not
  sufficient. The request enters a reviewed recovery flow with additional evidence, a delay, and a
  human decision under D16 authority.
- **Attacker-controlled contact.** Because an attacker who changes the primary channel would
  otherwise own recovery, a contact change keeps the previous channel able to report and reverse for
  the approved cooling-off period, and recovery evidence is evaluated against the state that existed
  before the change.
- **Recovery codes.** Where second factors are enrolled, single-use recovery codes are issued once,
  stored as digests, and counted; exhausting them requires reviewed recovery.
- **What recovery must never do.** It must not reveal whether an account exists, must not reactivate
  a `DELETED` account, must not bypass cooling-off, and must not be completable by a support agent
  acting alone without maker-checker and audited reason.

## Authorization model

### Capability catalog

Authorization is expressed as named capabilities, each with an owning domain, a scope type, a
required assurance level, and a disclosure class. Illustrative entries:

| Capability | Scope | Owner | Assurance |
| --- | --- | --- | --- |
| `BOOKING_CREATE` | Global (guest) | D08 | `L2` |
| `BOOKING_VIEW` | Booking | D08 | `L1` |
| `LISTING_EDIT` | Listing or portfolio | D03 | `L2` |
| `LISTING_PUBLISH` | Listing | D03 with D02 eligibility | `L2` |
| `CALENDAR_MANAGE` | Listing | D05 | `L2` |
| `PRICING_MANAGE` | Listing or portfolio | D07 | `L2` |
| `CONVERSATION_PARTICIPATE` | Booking or conversation | D12 | `L1` |
| `STATEMENT_VIEW` | Organization | D10 | `L2` |
| `PAYOUT_DESTINATION_CHANGE` | Organization | D10 | `L3` plus cooling-off |
| `MEMBER_MANAGE` | Organization | D01 | `L3` |
| `ORGANIZATION_TRANSFER` | Organization | D01 | `L3` plus maker-checker |
| `ACCOUNT_STATUS_CHANGE` | Global operator | D21 | `L3` plus reason |
| `CASE_ACCESS` | Case | D16 | `L3` plus purpose |

Roles remain in the contract as readable bundles — `GUEST`, `HOST`, `CO_HOST`, `ORG_OWNER`,
`ORG_MEMBER`, and the operator roles — but every decision expands a role to capabilities in a scope.
The bundle definition is versioned configuration, and a grant records both the role name and the
capability set effective when it was issued, so historical evidence stays interpretable after a
bundle changes.

### Grants and evaluation

A grant binds `(principal, capability set or role, scope type, scope id, effective interval, grantor,
reason)`. Evaluation for one request is:

```text
1. Resolve the session and principal; reject revoked, expired, suspended, or deleted.
2. Resolve the target resource to its owning account holder and market.
3. Collect grants matching the principal and any enclosing scope, effective at decisionInstant.
4. Expand roles to capabilities using the versioned bundle.
5. Subtract active scoped restrictions.
6. Require the action's capability; if absent, deny with a non-disclosing outcome.
7. Compare the session's assurance to the action's requirement; if insufficient, return a step-up
   outcome distinct from a denial.
8. Record the decision inputs for audit when the action is security relevant.
```

Deny wins over allow. A restriction always beats a grant. A more specific scope may not silently
widen a narrower one: holding `LISTING_EDIT` on one listing never implies portfolio-wide authority.

### Organizations, membership, and co-host delegation

- Membership makes a person part of an organization with a member role; it does not by itself grant
  resource capabilities beyond the role bundle for that organization's scope.
- Co-hosting is delegation to a person outside the organization, or a narrower grant inside it,
  covering a named listing or portfolio for a bounded interval.
- Every grant is bounded by the grantor's current authority. If a grantor loses `CALENDAR_MANAGE`,
  every derived grant for that scope becomes ineffective at the same instant; the derived rows are
  marked revoked with a derived-revocation reason rather than deleted.
- Invitations are single-use, expiring, digest-stored, and bound to the invited channel; accepting
  one requires a verified account and produces an audited membership or grant.
- Removing a member or revoking a grant closes future access immediately, terminates sessions whose
  only purpose was that scope where sessions are scoped, and never rewrites past attribution.
- Conflicting edits by two authorized members are resolved by the owning domain's optimistic
  concurrency, not by identity.

### Operator authority boundary

Operators authenticate as person accounts with operator grants; there is no shared account and no
generic superuser. Their authority is scoped by team, market, case assignment, monetary limit, and
purpose, as defined by [support](disputes-damage-claims-and-support.md) and D21 governance. D01
provides the operator principal, the assurance proof, the scope evaluation, and the audit record;
it never widens an operator's authority because a request is internal.

Break-glass access has a declared purpose, short expiry, immediate notification, immutable audit, and
mandatory post-use review. The existing `ADMIN` role is not an unrestricted authority and must be
decomposed into scoped operator capabilities before administrative commands are implemented.

## Risk, compliance, and restriction integration

- D15 may request: a challenge on a specific action, a scoped restriction with reason and expiry,
  revocation of specific sessions, mandatory step-up for a principal, or account-wide suspension when
  policy approves it. Each arrives as an authorized command with a decision reference; identity
  applies it idempotently, audits it, and reports the effective result.
- Identity never invents a risk decision, and a risk decision never writes identity tables directly.
- Restrictions are time-bounded. An indefinite restriction requires an explicit policy reference and
  a review date; a restriction whose expiry has passed is inert even before a cleanup worker removes
  it, and evaluation must not depend on the worker having run.
- D02 grants and revokes host capabilities from compliance outcomes. `host_profiles.identity_status`
  is evidence of a completed verification step at a point in time, not a permanent trust score and
  not authority for a future action.
- Appeals are owned by D15 and D16. Identity exposes the current effective state and its provenance
  so an appeal decision can be executed as a normal command.

## Conceptual data model

### Current tables

`users`, `user_roles`, `host_profiles`, and `auth_tokens` as described in
[Status and dependencies](#status-and-dependencies). They remain the historical foundation. Applied
changesets `000` through `010` are never edited; every change below is a new forward migration.

### Proposed tables

#### `account_holders`

Introduces the owning entity for supply, contracts, and settlement: holder ID, holder type
(`PERSON`, `ORGANIZATION`), status, market code, display name, created/updated instants, and version.
A person account holder references its `users` row one-to-one; an organization exists independently.
Backfilling one `PERSON` holder per existing user preserves current ownership without changing
behavior.

#### `organization_members`

Organization ID, user ID, member role, status (`INVITED`, `ACTIVE`, `SUSPENDED`, `REMOVED`), invited
by, joined and removed instants, and version, with a unique constraint on
`(organization_id, user_id)`. At least one `ACTIVE` owner must exist; the check is enforced by a
transactional guard plus a reconciliation query rather than a deferred constraint alone.

#### `capability_grants`

Grant ID, grantee principal type and ID, grantor principal type and ID, role name, materialized
capability set, scope type, scope ID, market, effective interval `[effective_from, effective_until)`,
reason code, source (`SELF_SERVICE`, `DELEGATION`, `COMPLIANCE`, `RISK`, `GOVERNANCE`), derived-from
grant ID, revocation instant and reason, and version. Indexes support point evaluation by
`(grantee_id, scope_type, scope_id)` filtered on effective interval, and derived-revocation traversal
by `derived_from_grant_id`.

#### `capability_restrictions`

Restriction ID, principal, capability or capability group, scope, reason code, requesting decision
reference, effective interval, expiry, lifting actor and instant, and version. Evaluation subtracts
active restrictions from grants; a restriction row is never edited to change its history.

#### `auth_sessions`

Session ID, principal, active organization context, created instant, authentication method, assurance
level, last assurance-proof instant, last-used instant, absolute and idle expiry, client descriptor,
hashed network origin, revocation state/reason/actor/instant, current refresh-token reference,
rotation generation, and version. Indexes support owner listing by `(user_id, revoked_at, expires_at)`
and expiry sweeps.

#### `auth_credentials`

Credential ID, principal, credential type, encoder or algorithm identifier, verifier digest or
encrypted secret reference with key version, enrolment instant, last-used instant, disabled instant,
and version, unique per principal and type where only one may exist. Password material continues to
live as a hash; second-factor secrets use a versioned key through the D00 secret boundary.

#### `contact_channels`

Channel ID, principal, channel type, normalized value, original value where retention policy allows,
purpose, primary flag, verification instant, verification method, superseded-by channel ID, and
version, with uniqueness on the normalized value of a verified primary channel per channel type. The
existing `users.email`/`users.phone_number` columns remain the compatibility surface until every
reader is migrated.

#### `auth_attempts`

Attempt ID, principal where resolved, identifier digest where not, attempt type, outcome class,
failure reason class, hashed source and device descriptor, decision reference where risk was
consulted, and occurred instant, retained for a bounded approved window. It supports velocity
controls and ATO investigation and holds no credential material.

#### `identity_audit_events`

Written through D00's append-only audit primitive rather than a private table where possible: action,
target principal or grant, outcome, reason code, acting principal and effective authority, approver,
assurance level at execution, correlation and causation identifiers, before/after references or
digests, and retention class. Application roles may insert but never update or delete.

#### `auth_tokens` evolution

The existing table gains a session reference, a rotation generation, a superseded-by reference, and a
consumption reason so rotation lineage and reuse detection are expressible. New token types are added
by widening the check constraint in a forward changeset, as `008` and `009` already do.

### Constraints and indexes

- Uniqueness: normalized verified primary email and phone per channel type; token digest;
  `(organization_id, user_id)`; one active credential per principal and type; one active primary
  channel per principal and type.
- Check constraints on every status, type, assurance level, and scope type, plus interval sanity
  (`effective_until` strictly after `effective_from`, `consumed_at` not before `created_at`).
- Partial indexes on unconsumed tokens by expiry, on active sessions by principal, and on effective
  grants by scope, so evaluation and sweeps stay bounded.
- Foreign keys inside the identity boundary; cross-domain references retain type and ID snapshots
  without cascading deletion so history survives.
- No index on raw personal contact text beyond the normalized uniqueness key; no credential material
  in any index.

### Migration, backfill, and deployment

1. Add `account_holders`, `contact_channels`, `auth_sessions`, `capability_grants`,
   `capability_restrictions`, `auth_attempts`, and audit support as additive changesets with nullable
   references.
2. Deploy writers that populate the new rows alongside existing behavior behind a kill switch;
   responses do not change.
3. Backfill one `PERSON` holder per user, one verified `contact_channels` row per non-null verified
   email or phone, and one `capability_grants` row per existing `user_roles` entry, in bounded
   checkpointed batches. Unknown provenance is recorded as legacy, never invented.
4. Reconcile counts and spot-check evaluation equivalence between the role check and the capability
   check for every existing protected route.
5. Switch authorization to capability evaluation; keep the role read as a fallback for one release.
6. Validate new not-null and check constraints once data is clean.
7. Remove the fallback in a later changeset after every supported application version has stopped
   reading it.

Existing session-less refresh tokens are adopted by creating a synthetic session per unconsumed
refresh token at switch time, or by requiring one re-login at switch; the choice is an open decision
with an explicit user-impact trade-off.

## Service boundaries

These are logical modules inside the modular monolith, not required network services.

| Module | Responsibility | Explicitly not responsible for |
| --- | --- | --- |
| `AccountDirectory` | Create, read, and transition person accounts, holders, and organizations | Host eligibility, reputation, listing content |
| `ContactChannelService` | Claim, verify, change, and supersede channels | Notification consent, template, delivery |
| `CredentialService` | Enrol, verify, rotate, and disable credentials | Deciding which action needs which factor |
| `AuthenticationService` | Evaluate a login, apply velocity and risk outcomes, create a session | Business authorization |
| `SessionService` | Issue, rotate, list, and revoke sessions and refresh lineages | Business meaning of a revoked session |
| `AssuranceEvaluator` | Compare session evidence to an action's requirement | Deciding the requirement, which the owning domain declares |
| `AuthorizationService` | Resolve grants, expand roles, subtract restrictions, answer capability checks | Domain rules beyond the capability |
| `DelegationService` | Issue, bound, expire, and cascade-revoke delegated grants | Whether delegation is commercially appropriate |
| `RecoveryService` | Execute automated recovery and route reviewed recovery | Human recovery adjudication (D16) |
| `IdentityAuditWriter` | Append security evidence through the D00 audit primitive | Authorizing the audited action |
| `IdentityFactsApi` | Serve the small read contract other domains consume | Caching decisions on consumers' behalf |

`AuthorizationService` must remain a decision function over stored grants. It does not gain
domain-specific conditions such as "a host may cancel within 24 hours"; those stay in the owning
domain, which asks identity only for the capability and assurance.

## API behavior

Routes below extend the existing `/api/v1/auth` and `/api/v1/users` surfaces. Existing paths and
payloads keep their contracts; new behavior is additive.

### Existing operations retained

| Method and path | Access | Target additions |
| --- | --- | --- |
| `POST /api/v1/auth/register` | Public | Idempotency key, holder creation, audit, event |
| `POST /api/v1/auth/login` | Public | Session creation, assurance, attempt record, risk hook, optional second factor |
| `POST /api/v1/auth/refresh` | Public | Session rotation generation, reuse detection, lineage revocation |
| `POST /api/v1/auth/logout` | Public | Session-scoped revocation and audit |
| `POST /api/v1/auth/password/forgot` | Public | Unchanged disclosure behavior, plus audit and rate evidence |
| `POST /api/v1/auth/password/reset` | Public | Session revocation, cooling-off, notification, audit |
| `POST /api/v1/auth/email-verification/request` | Bearer | Channel-scoped claim rather than user-scoped flag |
| `POST /api/v1/auth/email-verification/confirm` | Public | Channel verification instant and event |
| `GET /api/v1/users/me` | Bearer | Effective capability summary for the current scope |
| `GET /api/v1/users/email-exists` | Public | Decision required: keep with hard limits, protect, or remove |
| `PATCH /api/v1/users/me` | Bearer | Partial profile update; a replaced phone number re-enters verification |
| `PUT /api/v1/users/me/password` | Bearer | Reauthentication, revocation matrix, audit |
| `POST /api/v1/users/me/host-profile` | Bearer | Capability grant with compliance provenance |
| `DELETE /api/v1/users/me` | Bearer | Deletion request, obligation check, erasure schedule |

### Proposed operations

| Method and path | Access | Purpose |
| --- | --- | --- |
| `GET /api/v1/users/me/sessions` | Bearer | List own sessions with device, origin class, and last use |
| `DELETE /api/v1/users/me/sessions/{sessionId}` | Bearer, `L2` | Revoke one session |
| `POST /api/v1/users/me/sessions/revoke-all` | Bearer, `L2` | Sign out everywhere |
| `GET /api/v1/users/me/contact-channels` | Bearer | List channels and verification state |
| `POST /api/v1/users/me/contact-channels` | Bearer, `L3` | Claim a new channel and start verification |
| `POST /api/v1/users/me/contact-channels/{id}/primary` | Bearer, `L3` | Promote a verified channel after cooling-off |
| `POST /api/v1/users/me/factors` | Bearer, `L3` | Enrol a second factor |
| `DELETE /api/v1/users/me/factors/{id}` | Bearer, `L3` | Remove a factor, with notification |
| `POST /api/v1/auth/step-up` | Bearer | Raise the current session's assurance |
| `GET /api/v1/organizations/{id}/members` | Bearer, `MEMBER_MANAGE` | List membership |
| `POST /api/v1/organizations/{id}/invitations` | Bearer, `MEMBER_MANAGE`, `L3` | Invite a member |
| `POST /api/v1/invitations/{token}/accept` | Bearer | Accept an invitation |
| `DELETE /api/v1/organizations/{id}/members/{userId}` | Bearer, `MEMBER_MANAGE`, `L3` | Remove a member |
| `POST /api/v1/grants` | Bearer, scoped, `L3` | Delegate bounded capabilities |
| `DELETE /api/v1/grants/{id}` | Bearer, scoped, `L3` | Revoke a delegation and its derivatives |
| `GET /api/v1/users/me/permissions?scopeType=&scopeId=` | Bearer | Effective capabilities for one scope |
| `PUT /api/v1/admin/users/{userId}/status` | Operator, `ACCOUNT_STATUS_CHANGE`, `L3` | Suspend or reactivate with reason (**not implemented today**) |
| `POST /api/v1/admin/users/{userId}/sessions/revoke` | Operator, scoped, `L3` | Revoke sessions during an incident |
| `GET /api/v1/internal/principals/{id}/capabilities` | Workload | Internal evaluation for another domain |

Contract rules:

- every mutating identity command accepts an idempotency key and replays the original outcome for the
  same canonical input, following [platform foundation](platform-foundation.md);
- mutable resources expose a version, and stale writes return `RESOURCE_VERSION_CONFLICT`;
- responses never include a secret, a hash, another principal's contact value, or an unrelated
  account's existence;
- an operator command additionally requires a reason code and, where configured, maker-checker
  approval before it takes effect;
- collection endpoints are cursor paginated with bounded page size.

### Error semantics

| Condition | HTTP | Stable code | Retry and disclosure |
| --- | --- | --- | --- |
| Malformed input | `400` | `VALIDATION_ERROR` | Correct request; field paths only |
| Password fails policy | `400` | `VALIDATION_ERROR` | Never echo the rejected value |
| Wrong credential or unknown identifier | `401` | `INVALID_CREDENTIALS` | Identical for both cases |
| Missing or invalid access token | `401` | `UNAUTHORIZED` | Reauthenticate |
| Session revoked or expired | `401` | `SESSION_REVOKED` | Reauthenticate; no reason detail |
| Refresh secret invalid, consumed, or expired | `401` | `INVALID_REFRESH_TOKEN` | Reauthenticate; reuse handled server-side |
| Assurance insufficient | `401` with challenge metadata | `STEP_UP_REQUIRED` | Complete step-up, then retry |
| Authenticated but not permitted | `403` | `FORBIDDEN` | Do not retry unchanged |
| Account suspended or deleted | `403` | `ACCOUNT_DISABLED` | Contact support through a proving channel |
| Capability blocked by a restriction | `403` | `CAPABILITY_RESTRICTED` | Safe reason class and expiry only |
| Security change inside cooling-off | `403` | `SECURITY_COOLDOWN_ACTIVE` | Retry after the returned instant |
| Target account not visible to the caller | `404` | `USER_NOT_FOUND` | Enumeration safe |
| Email or phone already registered | `409` | `EMAIL_ALREADY_REGISTERED` | Only where policy already discloses it |
| Duplicate intent with different input | `409` | `IDEMPOTENCY_KEY_REUSED` | Operation and scope only |
| Stale version | `409` | `RESOURCE_VERSION_CONFLICT` | Reload and retry |
| Last owner or last credential removal | `409` | `LAST_AUTHORITY_REMOVAL` | Add a replacement first |
| Attempt or request limit exceeded | `429` | `RATE_LIMITED` | Honor a safe `Retry-After` |
| Risk or provider dependency unavailable | `503` | `DEPENDENCY_UNAVAILABLE` | Bounded retry with the same key |

`INVALID_CREDENTIALS`, `USER_NOT_FOUND`, and `EMAIL_ALREADY_REGISTERED` sit in different disclosure
classes and must not be mixed in one flow: registration may disclose a conflict where product policy
accepts it, while login and recovery must not.

## Event contracts

Identity publishes committed past-tense facts through the D00 transactional outbox using the shared
envelope. Payloads carry identities and minimal facts; consumers fetch protected detail through
authorized APIs.

| Event | Produced when | Primary consumers |
| --- | --- | --- |
| `UserRegistered` | Account created and committed | D19 analytics, D12 welcome intent, D15 baseline |
| `ContactVerified` | A channel's control is proven | D12 reachability, D02 eligibility, D15 |
| `ContactChangeRequested` / `ContactChanged` | Channel change starts and completes | D12, D15, D16 |
| `CredentialChanged` | Password or factor added, changed, or removed | D15, D12 notification |
| `SessionCreated` / `SessionRevoked` | Session lifecycle | D15, D19, D23 |
| `RefreshTokenReuseDetected` | A consumed secret is presented | D15 (high priority), D16 |
| `AuthenticationFailed` (aggregated) | Bounded failure summary, not per attempt | D15 velocity, D19 |
| `CapabilityGranted` / `CapabilityRevoked` | Grant lifecycle including derived revocation | Owning domains, D19, D21 |
| `OrganizationMemberAdded` / `Removed` | Membership lifecycle | D03, D10, D12 |
| `AccountStatusChanged` | Status transition with reason class | Every transactional domain |
| `AccountDeletionRequested` / `AccountErased` | Deletion lifecycle | D22, D19, all data holders |

Rules:

- no event carries a password, token, digest, one-time code, or full contact value; a channel is
  identified by its channel ID and type, with the value fetched under authorization when needed;
- `AccountStatusChanged` and `CapabilityRevoked` are the authoritative signals for consumers holding
  cached authority; each consumer must be able to invalidate within its documented freshness bound;
- personal-data erasure propagates as an explicit event, and consumers acknowledge completion so D22
  can evidence it;
- event names, versions, and payload governance follow
  [data and ML platform](data-experimentation-and-ml-platform.md); identity does not create a private
  taxonomy.

## Concurrency and idempotency

| Race | Winner and defense |
| --- | --- |
| Two registrations with the same email | Unique `citext` constraint; loser receives the registered conflict outcome |
| Two concurrent refreshes of one secret | Conditional update on the unconsumed row; exactly one successor is issued, the other is treated as reuse |
| Refresh concurrent with logout or suspension | Revocation and consumption contend on the same rows; a revoked session never yields a successor |
| Two password changes | Optimistic `version` on the credential; the loser reloads |
| Contact promotion concurrent with another verification | Unique verified-primary constraint per type |
| Grant revoked while a request is authorizing | Effective-interval evaluation at `decisionInstant` inside the command transaction; a grant revoked before commit does not authorize the commit |
| Delegated grant revoked while a derived grant is created | Derived creation re-reads the grantor's effective authority under lock and fails if it is gone |
| Last organization owner removed twice | Transactional guard plus reconciliation; one removal fails with `LAST_AUTHORITY_REMOVAL` |
| Restriction applied while a step-up completes | Restriction subtraction happens after assurance evaluation; the command is denied |
| Duplicate risk command | Idempotency scope keyed by the risk decision reference |
| Verification token issued twice | Issuing consumes prior unconsumed tokens of the same type in one transaction |
| Session sweep concurrent with use | Expiry is evaluated from stored instants, so a not-yet-swept expired session is already ineffective |

Idempotency scopes:

| Command | Scope | Replay behavior |
| --- | --- | --- |
| Register | Normalized identifier plus client key | Return the same account or the stable conflict |
| Create session (login) | Client key plus credential proof | Return the same session; never mint a second |
| Rotate refresh | Presented token digest | Exactly one successor; a second presentation is reuse |
| Change password | Account plus command key | Same result; do not double-revoke sessions differently |
| Grant or revoke capability | Grantor, grantee, scope, command key | Same grant identity |
| Status change | Target account plus decision reference | Same transition and audit entry |
| Erasure | Account plus erasure job reference | Idempotent transformation |

All identity state changes, their audit evidence, and their outbox facts commit in one short
transaction. External calls — email or SMS delivery, breached-credential lookup, risk evaluation
where synchronous — occur outside the transaction, and their failure never leaves a partially applied
security change.

## Security, privacy, and access control

### Authorization of identity's own operations

- A person may read and change only their own account, sessions, channels, credentials, and grants
  they issued.
- Organization data requires an effective grant in that organization's scope.
- Operator access requires an operator capability, a purpose, and, for personal-data reads, a case or
  approved reason recorded in audit; browsing accounts without a purpose is not an authorized use.
- Internal capability queries require a workload identity with a least-privilege capability, not a
  blanket internal allowance.

### Sensitive data handling

| Data | Classification | Handling |
| --- | --- | --- |
| Password and factor secrets | Secret | Hash or versioned-key encryption; never logged, exported, or returned |
| Opaque tokens and codes | Secret | Digest at rest; plaintext exists only in transit to the owner |
| Email, phone, and address | Personal | Normalized storage, purpose-scoped access, redacted in telemetry |
| Network origin and device descriptor | Personal | Hashed or truncated, bounded retention |
| Attempt and session history | Personal, security relevant | Bounded retention, purpose-scoped read, audited export |
| Identity audit evidence | Security evidence | Append-only, purpose-scoped, retention per D22 |
| Government identity documents | Restricted | Not stored by D01; D02 boundary owns them |

Redaction is an allowlist, not a best-effort regular expression over request bodies. High-cardinality
personal identifiers never become metric labels.

### Threats and required controls

| Threat | Control |
| --- | --- |
| Credential stuffing | Velocity limits, progressive delay, breached-credential check, risk challenge |
| Phishing and token theft | Short access-token lifetime, refresh rotation with reuse detection, session inventory, owner notification |
| Account takeover through recovery | Cooling-off, previous-channel notification and reversal, reviewed recovery, evidence evaluated against pre-change state |
| Privilege escalation through delegation | Grants bounded by grantor authority, derived revocation, no self-grant, assurance requirements |
| Insider or operator abuse | Scoped operator capabilities, purpose recording, maker-checker, break-glass expiry and review, audited reads |
| Enumeration | Uniform responses and comparable work across login, recovery, existence check, and invitation |
| Session fixation and replay | Server-generated session identity, rotation on privilege change, bound assurance upgrades |
| Audit tampering | Append-only writes, no application update or delete permission, integrity metadata |
| Erasure evasion or over-erasure | Approved transformation, propagation acknowledgement, retained integrity evidence, legal hold |

### Privacy, retention, and erasure

- Every identity data class has a purpose, lawful basis, retention class, and owner defined with D22
  and the [market contract](multi-market-compliance-and-localization.md).
- Deletion follows: request, obligation check, session and credential revocation, bounded window,
  erasure execution, propagation to logs, warehouse, features, projections, and backups per policy,
  and an evidenced completion record.
- Erasure replaces personal attribution with a stable pseudonymous reference so bookings, ledger
  entries, cases, and reviews remain internally consistent.
- A legal hold suspends erasure for named records with a recorded reason, owner, and review date; it
  does not silently extend retention for unrelated data.
- Export and access requests are authenticated, bounded, audited, and delivered only to a proven
  channel.

## Observability and operations

### Business and security metrics

- Registration, verification completion, and time to first verified channel.
- Login success, failure by class, second-factor completion, and step-up completion or abandonment.
- Sessions created, revoked by trigger class, and active sessions per principal distribution.
- Refresh rotations, reuse detections, and lineage revocations — reuse has a target near zero and
  pages when it rises above the approved threshold.
- Recovery requests, automated completions, reviewed escalations, and reversal reports.
- Grants issued and revoked by source, delegated grants active, and derived revocations.
- Restrictions applied, expired, and lifted, plus restriction age distribution.
- Deletion requests, blocked-by-obligation counts, erasure completions, and propagation backlog.

### Correctness and technical metrics

Targeted at zero, with alerting and reconciliation queries rather than metric-only detection:

- authorization decisions served without a resolvable grant record;
- sessions serving requests after their revocation instant beyond the documented window;
- accounts with no usable credential, or organizations with no active owner;
- verified primary channels duplicated across accounts;
- security-relevant changes committed without an audit event;
- identity events committed without an outbox row;
- restrictions active past their expiry in evaluation results.

Technical signals cover authentication latency, credential-hash cost, database contention on session
and token rows, token-sweep backlog, outbox lag for identity events, and dependency health for email,
SMS, and risk evaluation.

### Service-level objective candidates and alerts

Journey owners set numeric targets for login availability and latency, refresh availability, step-up
completion latency, session-revocation propagation, recovery-email delivery time, and reviewed
recovery turnaround. Each value has an owner, evidence, review date, and degradation response; no
number is invented in a library.

Alerts must page on: reuse-detection spikes, mass revocation, an unusual rate of successful logins
after failures, audit-write failures, credential-verification errors, and any correctness counter
leaving zero.

### Authorized recovery commands

Backend operations require read-only inspection plus authorized, reasoned, idempotent commands to:
revoke a principal's sessions during an incident; expire a stuck restriction after review; re-issue a
verification proof; re-drive erasure propagation for a named account; replay identity events to a
side-effect-safe consumer; rotate a signing key and confirm dual-verification readiness; and
reconcile grants against their granting domain. Every command records actor, reason, scope, approval,
result, and audit evidence. Direct database edits are not a recovery interface.

## Failure behavior

| Failure | Required behavior | Recovery and evidence |
| --- | --- | --- |
| Email or SMS provider unavailable during verification or recovery | Commit the proof record; queue delivery; tell the caller delivery is pending without disclosing existence | Retry through the durable notification path; owner may request again under cooldown |
| Best-effort in-process email listener lost after commit | Treated as a defect, not an accepted outcome; identity delivery moves to the durable outbox | Reissue on request; alert on the delivery gap |
| Risk evaluation times out during login | Deterministic fallback decides; the fallback is recorded on the session | Reconcile later; risk may revoke afterwards |
| Breached-credential service unavailable | Documented fail-open or fail-closed behavior; never silently skipped | Metric plus decision record |
| Database unavailable | Reject authentication rather than accept an unverified session | Readiness change and incident evidence |
| Crash between credential verification and session commit | No session exists; no audit claims success | Client retries the same idempotent login |
| Crash after session commit before the response | Session exists and is valid | Client replays the same key or re-authenticates; the orphan session expires |
| Refresh transaction rolls back after consuming | Consumption and issuance commit together, so neither applies | Client retries with the original secret |
| Consumed refresh secret presented | Reject, revoke the lineage, signal risk, notify the owner | Owner re-authenticates; incident evidence retained |
| Grant revoked mid-request | The command fails or, if already committed, the effect stands and is attributed | Audit shows authority effective at commit |
| Restriction command arrives twice | Idempotent by decision reference | Same effective state |
| Audit append fails for a security change | Roll back and deny the change | Repair audit dependency; never proceed invisibly |
| Outbox append fails for a required identity fact | Roll back the producer transaction | Retry the original command |
| Signing key unavailable or revoked | Fail closed for token issuance; existing valid tokens continue only until expiry | Rotate and restore an approved version |
| Erasure propagation partially fails | Do not mark erasure complete | Retry per consumer, escalate, evidence the gap |
| Backup restored to an earlier point | Sessions and tokens valid before the restore point may resurrect | Mass revocation and reconciliation before traffic activation |

## Testing and verification

The repository currently has no tests, so this section defines what the implementation must add; it
does not imply existing coverage.

### Deterministic and property tests

- Email and phone normalization, canonical comparison, and Unicode edge cases.
- Password policy boundaries, the 72-byte input limit, and encoder-identifier handling.
- Assurance comparison across level, freshness expiry, and clock boundaries with a fixed clock.
- Capability expansion from role bundles, deny-over-allow precedence, and restriction subtraction.
- Grant effectiveness exactly at `effective_from` and `effective_until` boundaries.
- Delegation bounding: a derived grant can never exceed the grantor's capability set.
- Token digest computation, single-use semantics, and expiry equality treated as expired.

### Real-database and concurrency tests

- Concurrent registration with the same email produces one account.
- Concurrent refresh of one secret produces exactly one successor; the loser is classified as reuse.
- Refresh racing logout, suspension, and deletion never yields a usable successor.
- Concurrent grant creation and grantor revocation cannot leave an orphan derived grant.
- Removing the last organization owner or the last usable credential fails.
- Verified primary channel uniqueness holds under concurrent promotion.
- State change, audit row, and outbox row commit atomically under injected failure at each write.
- Expired sessions, restrictions, and tokens are ineffective before any sweep worker runs.

### Security and privacy tests

- Cross-user, cross-organization, revoked-delegation, suspended-actor, and stale-session access are
  denied at the command, not only at the route.
- Header, body, and claim spoofing cannot select the actor, role, organization, or assurance level.
- Login, recovery, invitation, and existence-check responses do not distinguish existence by body,
  code, or measurable timing.
- Logs, traces, metrics, events, error bodies, audit rows, and idempotency projections contain no
  seeded credential, token, code, or full contact value.
- Session revocation stops access within the documented bound; a revoked session cannot refresh.
- Reuse detection revokes the lineage, emits the risk signal, and notifies exactly once.
- Operator commands require capability, assurance, reason, and — where configured — maker-checker;
  break-glass expires and is reviewed.
- Audit rows cannot be updated or deleted by the application role.
- Erasure removes personal data from every declared store and preserves the pseudonymous join key.

### Contract and recovery tests

- OpenAPI documents every identity error code, step-up challenge, idempotency, version, and
  pagination behavior.
- Producer and consumer fixtures cover every identity event version and quarantine unknown versions.
- Backfill equivalence: capability evaluation matches the legacy role check for every existing route
  before the switch.
- Restore-from-backup exercises mass revocation and reconciliation before service activation.

## Caching, performance, and scaling

Authoritative identity state stays in PostgreSQL for the target release.

Critical query shapes:

- point lookup of a principal by normalized identifier for login;
- session load by session ID with revocation and expiry columns covered by the index;
- effective grants for `(principal, scope type, scope id)` filtered on the effective interval;
- active restrictions for a principal;
- unconsumed token lookup by digest;
- owner session listing and bounded expiry sweeps.

Caching rules:

- an authorization decision required for a state-changing command is not served from cache; it is
  evaluated inside the command's transaction;
- read-path capability results may be cached with owner, key, version, short time to live,
  invalidation on `CapabilityRevoked`/`AccountStatusChanged`/`SessionRevoked`, and a documented
  maximum staleness that the owning domain accepts;
- role bundle definitions are versioned configuration and cache well; grants and restrictions do not;
- no cache may extend the effective life of a revoked session, grant, or restriction beyond its
  documented bound.

Cost control: adaptive password hashing is deliberately expensive, so login concurrency, work factor,
and connection pool sizing are tuned together and measured; a work-factor change is a rollout with
dual-verification support, not an edit. Session, token, attempt, and audit tables grow monotonically;
retention, partitioning, and bounded sweeps are planned before volume forces them. Extracting
identity into a separate service, adopting an external identity provider, or introducing a
distributed session store are measured-scale capabilities requiring an owner, contract, SLO, and
evidence that the benefit exceeds the new consistency and recovery cost.

## Appropriate use of AI

Identity decisions are deterministic. No model authenticates a person, grants or revokes a
capability, approves recovery, decides an account is compromised, or interprets an authorization
rule.

Bounded, valid uses sit outside the authorization path:

- risk models owned by [trust and safety](trust-safety-fraud-and-moderation.md) may score login,
  recovery, and account-change events; identity consumes the returned decision, never a raw score,
  and always has a deterministic fallback plus a kill switch;
- anomaly detection over session and attempt telemetry may raise investigation candidates for human
  review;
- support-side summarization of an account timeline may assist an agent under
  [support](disputes-damage-claims-and-support.md), with citations to internal evidence and no
  authority to execute an identity command.

Every model-influenced identity outcome records the decision reference, model and feature versions,
and fallback status, and remains explainable to the affected person through an approved reason class.
Prohibited outright: model-decided authentication, model-decided authority, model-generated
credentials or tokens, and model-authored security notifications that assert a fact the system has
not verified.

## Target-release dependencies and completion gates

These are cumulative implementation dependencies for one release, not reduced product versions.

### Dependency 0 — Decisions and ownership

Approve the principal model, capability catalog and role bundles, assurance levels and their required
actions, session lifetimes, recovery policy, cooling-off durations, operator authority decomposition,
retention classes, and the fate of the public email-existence endpoint.

Gate: each item has an owner, decision record, alternatives and consequences, effective date, and
revisit trigger.

### Dependency 1 — Evidence and session foundation

Add `auth_sessions`, `auth_attempts`, identity audit evidence, and session references on tokens;
write them from the existing flows without changing response contracts.

Gate: every existing authentication flow produces a session row, an attempt record, and audit
evidence; session listing and revocation work for the owner; no response contract regressed.

### Dependency 2 — Token integrity

Implement refresh reuse detection, lineage revocation, absolute session lifetime, the documented
revocation matrix, and owner notification.

Gate: presenting a consumed secret revokes the lineage, signals risk, notifies once, and is proven by
concurrency and failure-injection tests.

### Dependency 3 — Capability authorization

Add `account_holders`, `capability_grants`, and `capability_restrictions`; backfill from
`user_roles`; switch every protected route to capability evaluation with equivalence evidence.

Gate: no protected command authorizes from a role alone or from a token claim; a scoped restriction
denies precisely without suspending an account; equivalence tests pass for every existing route.

### Dependency 4 — Organizations and delegation

Add organizations, membership, invitations, and delegated grants bounded by the grantor's authority,
with derived revocation.

Gate: a co-host can operate exactly the delegated scope; revoking the grantor's authority immediately
disables every derived grant; the last-owner invariant holds under concurrency.

### Dependency 5 — Assurance, contact integrity, and recovery

Add assurance levels, step-up, reauthentication, second-factor enrolment, phone verification,
contact-change protection with cooling-off and reversal, and reviewed recovery.

Gate: every `L3` action rejects an insufficient session with a distinct step-up outcome; a
contact-change takeover attempt is reversible by the previous channel within the approved window.

### Dependency 6 — Operator authority and governance integration

Decompose `ADMIN` into scoped operator capabilities, implement the status-change and incident
revocation commands with reason and maker-checker, and accept D15 restriction and revocation commands.

Gate: no unrestricted administrator exists; every operator action carries capability, purpose,
assurance, and audit; break-glass expires and is reviewed.

### Dependency 7 — Events, privacy, and erasure

Publish identity events through the outbox, and implement deletion request, obligation checks,
erasure, propagation acknowledgement, legal hold, and data export.

Gate: erasure completes and is evidenced across every declared store while contractual and financial
history remains internally consistent through pseudonymous references.

### Required target capability

Dependencies 0–7 are required target behavior. D01 is not complete because login and registration
work; resource-scoped authorization, session integrity, delegation bounding, recovery safety, operator
scoping, and erasure are part of the gate.

### Designed extension boundaries

- External identity providers, enterprise single sign-on, and passkeys must be addable as new
  credential types and provider bindings without changing the principal, session, or capability
  contracts.
- Additional markets reuse the same principal and capability model with market-keyed policy.
- Additional operator teams and capabilities are configuration, not new account states.

### Measured-scale capabilities

A distributed session or token-introspection store, a dedicated identity service, an external
identity platform, and per-request revocation checks in a shared cache activate only on measured
evidence of latency, contention, or organizational need, with an owner, rollback, and post-launch
review trigger.

## Verification checklist

### Functional and authority correctness

- [ ] Every protected command authorizes the actor against the concrete resource, owner, market, and
  account state, not a role name or token claim.
- [ ] Capability evaluation subtracts active restrictions and applies deny-over-allow.
- [ ] A delegated grant can never exceed, outlive, or survive the grantor's authority.
- [ ] Every organization retains an active owner and every person account retains a usable credential.
- [ ] A verified primary contact channel is unique, and changing it restarts verification with
  notification and cooling-off.
- [ ] Every `L3` action rejects an insufficient session with a step-up outcome distinct from denial.
- [ ] Identity error codes have one meaning, one status, and one disclosure class across all routes.

### Concurrency and recovery

- [ ] Concurrent registration, refresh, promotion, and grant races each resolve through a database
  constraint, not only application logic.
- [ ] A consumed refresh secret revokes its lineage, signals risk, and notifies exactly once.
- [ ] State change, audit evidence, and outbox facts commit atomically or not at all.
- [ ] Expired sessions, grants, restrictions, and tokens are ineffective without waiting for a worker.
- [ ] Duplicate risk, operator, and recovery commands replay one outcome by decision reference.
- [ ] Restore-from-backup performs mass revocation and reconciliation before activation.

### Security and privacy

- [ ] No credential, token, code, digest, or full contact value appears in logs, traces, metrics,
  events, error bodies, or idempotency projections.
- [ ] Login, recovery, invitation, and existence checks do not disclose account existence by body,
  code, or measurable timing beyond approved policy.
- [ ] Session revocation stops access within the documented bounded window.
- [ ] Operator access is capability-scoped, purpose-recorded, audited, and maker-checked where
  configured; break-glass expires and is reviewed.
- [ ] Identity audit rows are append-only and cannot be updated or deleted by the application role.
- [ ] Erasure removes personal data across declared stores, preserves the pseudonymous join key, and
  is evidenced; legal hold suspends it explicitly.

### Operations

- [ ] Reuse detection, mass revocation, audit-write failure, and every correctness counter have
  numeric thresholds, owners, and paging behavior.
- [ ] Session, token, attempt, and audit growth has retention, sweep, and capacity plans.
- [ ] Recovery commands are bounded, idempotent, authorized, reasoned, audited, and dry-runnable.
- [ ] Login latency, hashing cost, and connection pool sizing are measured together with a documented
  work-factor rollout path.
- [ ] The documentation set, including the root README and GUIDE, describes only implemented routes as
  implemented.

## Decisions required before implementation

Each consequential choice should be recorded in an architecture decision record (ADR) with owner,
date, context, alternatives, decision, consequences, rollout, and revisit trigger.

1. Is an organization required for business hosts in Vietnam, may one person own several host
   organizations, and how does a personal portfolio convert to an organization portfolio?
2. What is the capability catalog, which role bundles exist, and who approves changes to a bundle?
3. Which actions require `L2` versus `L3`, and is a second factor mandatory for operators and for
   principals holding payout authority?
4. What are the access-token lifetime, session idle expiry, and absolute session lifetime, and what
   revocation-propagation window is acceptable for each journey?
5. Does the request path consult a session revocation check, or is the short access-token lifetime the
   accepted bound?
6. Which second-factor methods are supported at launch, and is phone verification required for
   Vietnam recovery and notification?
7. What cooling-off durations apply to password reset by recovery, primary-contact change, and factor
   removal, and may an operator waive one under maker-checker?
8. What evidence, delay, and human authority define reviewed account recovery, and who owns that
   queue?
9. Is a breached-credential check adopted, from which source, and does it fail open or closed?
10. Is `PENDING_VERIFICATION` introduced, and which capabilities does an unverified account hold?
11. What obligations block deletion completion, how long is the `DELETION_REQUESTED` window, and who
    may cancel it?
12. What is erased versus pseudonymized on account erasure, in which stores, and what integrity
    evidence is retained under which lawful basis?
13. Is the public `GET /api/v1/users/email-exists` endpoint retained, protected, or removed?
14. How is `ADMIN` decomposed into scoped operator capabilities, and which operator actions require
    maker-checker or break-glass?
15. Which identity commands may D15 invoke directly, and what scoped-restriction vocabulary does
    identity accept?
16. Are external identity providers or passkeys in the target release, and if not, what contract
    space is reserved for them?
17. How do existing refresh tokens adopt sessions at switch time — synthetic sessions or one forced
    re-login?
18. What retention windows apply to attempts, sessions, audit evidence, and hashed network origins in
    Vietnam?
19. Which identity events are published, at what sensitivity class, and which consumers must
    acknowledge erasure propagation?
20. Which password work factor is used, and what is the rollout and dual-verification plan for
    changing it?
