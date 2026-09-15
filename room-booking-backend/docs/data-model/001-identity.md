# Migration 001 — Identity foundation (retired)

## Historical note

This migration created `users`, `user_roles`, and `host_profiles`: the first identity schema, built
before the target principal model existed. Migration
[`014`](014-identity-target-model.md) added `account_holders` and its satellites beside these three
tables without replacing them, and migration [`037`](037-identity-retire-legacy-tables.md) completed
the cutover — repointing every foreign key that pointed at `users` through `account_holders`, then
dropping `host_profiles`, `user_roles`, and `users` in that order. None of the three tables this
migration created still exists.

This document is kept for history. For the schema and service rules these tables were replaced by,
see [`014-identity-target-model.md`](014-identity-target-model.md) and
[`037-identity-retire-legacy-tables.md`](037-identity-retire-legacy-tables.md). For the live
authorization design, see
[`../features/identity-accounts-and-access.md`](../features/identity-accounts-and-access.md) and
[`../modules/identity.md`](../modules/identity.md).

## What used to be here

- `users`: account identity and verification state. Superseded by `account_holders` plus
  `contact_channels` and `auth_credentials`.
- `user_roles`: a user may be both guest and host. Superseded by `capability_grants`, whose
  `role_name` column carries the same convenience label but whose materialized `capabilities` array
  is what authorization decisions actually evaluate.
- `host_profiles`: host-only profile and identity-verification state. Its `bio`, `average_rating`,
  and `review_count` were never this module's facts to own (see
  [`../features/identity-accounts-and-access.md`](../features/identity-accounts-and-access.md) §
  Profile facts and their consumers); host identity verification lives in `hostverification`'s
  `host_legal_profiles` (migration `015`), and no module yet owns the public host profile.
