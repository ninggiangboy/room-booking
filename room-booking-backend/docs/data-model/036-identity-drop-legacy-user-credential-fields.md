# Migration 036 — Drop legacy user credential fields

## Goal

Remove three columns from `users` that migration `014`'s backfill already copied into the target
model and that no application code had read or written since: `password_hash`,
`email_verified_at`, `phone_verified_at`.

## What changed

- `users.password_hash` → superseded by `auth_credentials` (`PASSWORD` credential type).
- `users.email_verified_at` / `phone_verified_at` → superseded by `contact_channels.verified_at`.

All three were dead columns: live code already read and wrote the target-model equivalents, and
nothing in the codebase referenced the `users` columns anymore. Dropping them was the first
cleanup step, ahead of the full legacy-table retirement in migration
[`037`](037-identity-retire-legacy-tables.md).

## Exit criteria

- `users` carries no password or verification-timestamp columns.
- Registration, login, and verification continue to operate entirely through `auth_credentials` and
  `contact_channels`.
