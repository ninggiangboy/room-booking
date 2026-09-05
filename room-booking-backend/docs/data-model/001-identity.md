# Phase 001 — Identity

## Goal

Support registration, authentication identity, authorization roles, and optional host onboarding.

## Tables

- `users`: account identity and verification state.
- `user_roles`: a user may be both guest and host.
- `host_profiles`: host-only profile and identity-verification state.

## Service rules

- Normalize email before writing even though `citext` protects uniqueness.
- Store only a modern password hash; never store credentials or verification tokens in these tables.
- Add `HOST` and create `host_profiles` in the same transaction when onboarding completes.
- Treat `average_rating` and `review_count` as read-model counters whose source of truth is `reviews`.

## Exit criteria

- A user can register with a unique email.
- Roles can be granted without creating a second account.
- Suspending or soft-deleting an account prevents authentication while preserving booking history.
