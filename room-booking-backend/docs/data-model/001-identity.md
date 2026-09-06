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
- Future contact preferences, consent evidence, transactional-notification routing, booking
  participants, and purpose-bound support access follow
  [`../features/messaging-notifications-and-stay-operations.md`](../features/messaging-notifications-and-stay-operations.md);
  the existing authentication email sender is not that durable notification platform.

## Exit criteria

- A user can register with a unique email.
- Roles can be granted without creating a second account.
- Suspending or soft-deleting an account prevents authentication while preserving booking history.

## Implementation status

Complete. Registration creates the initial `GUEST` role, host onboarding atomically creates a
`host_profiles` row and grants `HOST`, administrators can move non-deleted accounts between
`ACTIVE` and `SUSPENDED`, and users can soft-delete only their own account. `DELETED` is terminal.
Protected requests reload status and roles from the database, while suspension and deletion also
revoke every outstanding opaque authentication token.
