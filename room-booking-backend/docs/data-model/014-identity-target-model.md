# Migration 014 — Identity, sessions, and capability authorization

## Goal

Move from "what role does this user have" to "may this principal do this thing, to this resource,
right now, and who said so" — without a login outage.

The role model cannot express the things the target release depends on. Owning a listing is stronger
than holding the `HOST` role. An organization acts through members who are not itself. A compliance
hold must remove a capability without removing the role that explains why it was held. A replayed
refresh token must be distinguishable from an ordinary one.

## Tables

- `account_holders`: the entity that owns supply, contracts, and is settled — a person or an
  organization. A user is who signs in; a holder is who the platform does business with.
- `organization_members`: who belongs to an organization and in what role.
- `capability_grants`: authority, scoped to a resource, over an effective span, with provenance.
- `capability_restrictions`: withdrawals evaluated *on top of* grants, never by editing them.
- `auth_sessions`: one durable sign-in, owning the refresh-token lineage issued under it.
- `auth_credentials`: enrolled authentication material, at most one live per type per principal.
- `contact_channels`: how a principal is reached, and what has been proven about it.
- `auth_attempts`: attempt evidence for velocity control and takeover investigation.
- `auth_tokens`: gains `session_id`, `rotation_generation`, `superseded_by`, `consumption_reason`.

Identity audit is written through the D00 `audit_events` primitive from migration `012` rather than a
private table, so there is one append-only trail rather than one per domain.

## Design rules

- **This migration is additive.** `users`, `user_roles`, and `host_profiles` keep working and remain
  the compatibility surface. The identity design stages the cutover across releases — add tables,
  write both, backfill, reconcile, switch reads, then drop the fallback — and collapsing those steps
  into one migration is how a login outage happens. **Steps 5–7 have not been done: authorization
  still evaluates roles.**
- **Restrictions subtract; they do not edit.** Keeping both facts — the principal was granted this,
  *and* a decision currently suppresses it — is what gives an appeal something to restore.
- **Scope is what a role could not say.** `ck_capability_grants_scope_id` forces every non-global
  grant to name its resource, which is how "owns this listing" becomes expressible.
- **Exclusivity follows proof, not claim.** Two accounts may each register the same address — people
  mistype, and an attacker should not be able to squat an address by claiming it first — but
  `uk_contact_channels_verified_primary_value` makes only a *verified primary* channel unique.
- **A credential is a verifier or a secret reference, never both and never neither.** Material we
  only check (a password) can never be read back; material we must use (a TOTP seed) goes through the
  secret boundary with its key version.
- **A consumed token must say why.** Without `consumption_reason`, normal rotation and an attacker
  replaying a stolen secret produce identical rows, and reuse detection has nothing to detect.
  `AuthToken.consume(instant, reason)` sets both fields together so a caller cannot supply one.
- **Two session expiries are kept deliberately.** Idle expiry ends a quiet session; absolute expiry
  ends one that has lived too long however actively it is used, so a stolen session cannot be kept
  alive by using it.
- **"At least one active owner" is not a row constraint.** The rule is about the absence of other
  rows, so it is enforced by `countActiveOwnersForUpdate` inside the transaction that would break it;
  two concurrent removals would otherwise each see one owner remaining and leave none.
- **Lease and expiry indexes carry no time predicate**, per
  [`../conventions/04-time-and-clock.md`](../conventions/04-time-and-clock.md): a partial index whose
  predicate moves is not immutable, so sweeps bind their own decision instant.

## Backfill

Changeset `014-10` gives every existing account its target-model representation: one `PERSON` holder
per user, a primary `EMAIL` channel per user and a `PHONE` channel where one exists, a legacy
`PASSWORD` credential, and one grant per `user_roles` row.

Provenance is preserved rather than invented. Timestamps are copied from the source row, because the
holder did not come into existence when the migration ran. Market context is left null and marked
`LEGACY_UNRECONCILED` — the multi-market design forbids inferring a market from a phone number or an
address, and an unreconciled holder is blocked from consequential workflows until an operator
resolves it, which is the intended outcome rather than a defect.

`users.email` and `users.phone_number` are already unique, so the verified-primary uniqueness cannot
be violated by the backfill.

The role-to-capability mapping in the backfill is a **legacy equivalence, not a new authorization
design**. Step 4 of the identity migration plan reconciles it: every protected route must evaluate
identically under the role check and the capability check before any read switches over.

## Open decision, not yet taken

Existing session-less refresh tokens have no `auth_sessions` row. The identity design leaves the
choice explicit: either synthesize a session per unconsumed refresh token at switch time, or require
one re-login. The second is simpler and strictly safer; the first avoids signing every active user
out. **This has not been decided, and the read cutover should not happen until it is.**

## Exit criteria

- Every existing user has exactly one person holder, and every role has exactly one grant.
- A listing-scoped grant authorizes an action on that listing and on no other.
- A restriction suppresses a capability while leaving the grant that explains it intact.
- A refresh token replayed after rotation is distinguishable from a first use.
- Removing the last active owner of an organization fails, including under concurrency.
