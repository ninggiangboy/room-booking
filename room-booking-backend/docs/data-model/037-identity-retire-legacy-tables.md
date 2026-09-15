# Migration 037 — Retire the legacy identity schema

## Goal

Complete the cutover migration [`014`](014-identity-target-model.md) staged across releases:
repoint every foreign key that pointed at `users` through `account_holders`, then drop
`host_profiles`, `user_roles`, and `users`. `account_holders` becomes the identity module's single
principal root — the identifier every JWT subject claim names, and the row every satellite table
points at.

There was no data to migrate in this application (see `docs/data-model/README.md` § *What the
schema does not yet prove*), which is exactly why doing every rewrite honestly, rather than
skipping it, cost nothing here.

## What changed

- **`account_holders`** gained `avatar_url` (backfilled from `users.avatar_url`, the one column with
  no other home in the target model), lost `user_id` and its supporting FK/check/unique index, and
  had `context_state`'s `LEGACY_UNRECONCILED` value renamed to `UNRESOLVED` — "legacy" stopped
  naming anything once the legacy schema was gone.
- **`contact_channels`, `auth_credentials`, `auth_sessions` (`user_id` and `revoked_by`),
  `auth_tokens`, `auth_attempts`, `organization_members` (`user_id` and `invited_by`)** — every
  `user_id`-shaped column was rewritten through the `account_holders.user_id` join, renamed
  (`user_id` → `account_holder_id`, `organization_members.user_id` → `member_holder_id`,
  `invited_by`/`revoked_by` → `invited_by_account_holder_id`/`revoked_by_account_holder_id`), and
  repointed at `account_holders`.
- **`capability_grants`** — `grantee_id` was rewritten from user ids to holder ids, `grantee_type
  'USER'` became `'PERSON'`, and `source 'LEGACY'` became `'SELF_SERVICE'`, with both check
  constraints narrowed to match. This also fixed a latent bug: the `014-10` backfill wrote
  `'USER'`/`'LEGACY'` values the Java `PrincipalType` and `GrantSource` enums never declared, so
  reading a backfilled grant through `CapabilityGrantRepository` would have thrown — nothing read
  grants before this release, which is the only reason it never surfaced. Any `user_roles` row with
  no corresponding grant was backfilled one, so nothing loses its role in the cutover.
- **`verification_appeals.submitted_by`, `property_collaborators.user_id`** — the only two foreign
  keys into `users` from outside `identity`, repointed and renamed the same way
  (`submitted_by_account_holder_id`, `account_holder_id`). A broader grep also turns up
  `fk_listings_host`, `fk_bookings_guest`/`fk_bookings_host`, `fk_reviews_reviewer`/
  `fk_reviews_reviewee`, and `fk_favorites_user` in migrations `002`/`004`/`006`, but those tables
  were dropped by `016-01-retire-historical-listing-stack` well before this migration runs; the
  `016`-created `listings`/`properties` catalog reaches its account holder through
  `properties.account_holder_id`, which has referenced `account_holders` directly since the moment
  it was created. Nothing outside the two named tables needed repointing.
- **`auth_tokens`** gained `ck_auth_tokens_refresh_requires_session`, requiring every refresh token
  to carry a session — see § *Resolved* in [`014`](014-identity-target-model.md#resolved-session-less-refresh-tokens).
- `DROP TABLE host_profiles; DROP TABLE user_roles; DROP TABLE users;` — in that order, so no
  foreign key blocks the drop.

## Design rules

- **Rewrite through the join before dropping it.** `account_holders.id` is a fresh UUID, not equal
  to `users.id`; every child column had to be rewritten (`UPDATE ... FROM account_holders h WHERE
  h.user_id = child.user_id`) before `account_holders.user_id` itself could be dropped.
- **A column named `user_id` that points at `account_holders` is actively misleading** once `users`
  is gone, and this was the last moment the rename was free — the alternative (repoint the FK, keep
  the name) was available and not taken.
- **Forward-only.** No previously applied changeset was edited; this migration only adds new ones.

## Exit criteria

- `SELECT to_regclass('users'), to_regclass('user_roles'), to_regclass('host_profiles');` returns
  three nulls.
- Every foreign key that used to reference `users` now references `account_holders`.
- `capability_grants` contains no `'USER'` grantee type, no `'LEGACY'` source, and no orphaned role.
