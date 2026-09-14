# Room Booking

Backend for a room-booking platform, built with Java and Spring Boot. The application currently exposes identity, authentication, and host onboarding. The database carries the full target marketplace schema — 423 tables across 35 Liquibase migrations, covering supply, inventory, pricing, booking, payment, ledger, cancellation, messaging, stay operations, reviews, trust and safety, disputes, discovery, analytics, machine learning, governance, host operations, and growth — with matching Spring Data JDBC aggregates and repositories, but no services on top of it yet.

## Current features

- Guest registration and login
- JWT access tokens
- Rotating, database-backed refresh tokens
- Logout and refresh-token revocation
- User-requested email verification with cooldown and rolling rate limits
- Forgot/reset password with short-lived, single-use email tokens
- Current-user lookup and password changes
- Atomic host onboarding with `HOST` role assignment
- Administrator-controlled suspension/reactivation and user-initiated account soft deletion
- Immediate rejection of access tokens belonging to suspended or deleted accounts
- Consistent JSON error responses
- Liquibase-managed PostgreSQL and PostGIS schema

> Everything beyond the list above is schema and persistence only. The tables, constraints, triggers, aggregates, and repositories exist; the services and HTTP endpoints that would drive them do not. Migration `014` also created a target identity model that the running authentication code has not yet been moved onto — see
> [the data-model README](room-booking-backend/docs/data-model/README.md) before changing identity code.

## Tech stack

- Java 25
- Spring Boot 4.1
- Spring Security
- Springdoc OpenAPI and Swagger UI
- Spring Data JDBC
- PostgreSQL 17 with PostGIS 3.6
- Liquibase
- Gradle
- Mailpit for local email testing
- MinIO for target media and protected-evidence object storage

## Prerequisites

- JDK 25
- Docker with Docker Compose

Gradle does not need to be installed separately because the project includes the Gradle Wrapper.

## Quick start

Run all backend commands from `room-booking-backend`:

```bash
cd room-booking-backend
docker compose -f compose.local.yaml up -d
./gradlew bootRun --args='--spring.profiles.active=local'
```

The API is available at `http://localhost:8080`. Verify that it is running:

```bash
curl http://localhost:8080/actuator/health
```

Local services:

| Service | Address |
| --- | --- |
| PostgreSQL + PostGIS | `localhost:5432` |
| MinIO API | `http://localhost:9000` |
| MinIO console | `http://localhost:9001` |
| Mailpit SMTP | `localhost:1025` |
| Mailpit UI | `http://localhost:8025` |

Stop the local infrastructure with:

```bash
docker compose -f compose.local.yaml down
```

## API overview

| Method | Endpoint | Authentication | Description |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Public | Register a guest account |
| `POST` | `/api/v1/auth/login` | Public | Log in and receive a token pair |
| `POST` | `/api/v1/auth/refresh` | Public | Rotate a refresh token |
| `POST` | `/api/v1/auth/logout` | Public | Revoke a refresh token |
| `POST` | `/api/v1/auth/email-verification/confirm` | Public | Verify an email address |
| `POST` | `/api/v1/auth/email-verification/request` | Bearer token | Request a verification email |
| `POST` | `/api/v1/auth/password/forgot` | Public | Request a password-reset email |
| `POST` | `/api/v1/auth/password/reset` | Public | Reset a password with a one-time token |
| `GET` | `/api/v1/users/email-exists?email=...` | Public | Check whether an email is registered |
| `GET` | `/api/v1/users/me` | Bearer token | Get the current user |
| `PUT` | `/api/v1/users/me/password` | Bearer token | Change the current user's password |
| `POST` | `/api/v1/users/me/host-profile` | Bearer token | Create a host profile and grant the `HOST` role |
| `DELETE` | `/api/v1/users/me` | Bearer token | Soft-delete the current account and revoke opaque tokens |
| `PUT` | `/api/v1/admin/users/{userId}/status` | `ADMIN` bearer token | Activate or suspend a non-deleted account |

Protected endpoints expect an access token:

```http
Authorization: Bearer <access-token>
```

## OpenAPI documentation

When the API is running, explore its interactive documentation at
[`/swagger-ui.html`](http://localhost:8080/swagger-ui.html). The generated OpenAPI contract is
available as [JSON](http://localhost:8080/v3/api-docs) and
[YAML](http://localhost:8080/v3/api-docs.yaml). Use Swagger UI's **Authorize** control to provide
an access JWT for protected operations.

Swagger UI organizes operations into **Authentication** and **Users**. Bearer JWT authentication
is the OpenAPI default; public operations explicitly override it and do not show a lock. Each
operation documents its request body or parameters, successful response schema, and expected error
responses. Repeated errors (such as `401`, disabled-account `403`, validation `400`, and `500`)
are reusable entries in `components/responses`; operations reference them rather than duplicating
their description and `ApiErrorResponse` schema. Inspect the error body's `code` field rather than
parsing its human-readable `message`.

## Configuration

Shared configuration is stored in `room-booking-backend/src/main/resources/application.properties`. Development defaults are in `application-local.properties` and match `compose.local.yaml`.

Important settings include:

- `spring.datasource.*` for PostgreSQL
- `spring.mail.*` for SMTP
- `app.email-verification.*` for verification links, token lifetime, cooldown, and rate limit
- `app.password-reset.*` for reset links and token lifetime
- `security.jwt.*` for token signing and expiration
- `springdoc.*` for OpenAPI and Swagger UI paths
- `spring.jackson.time-zone` and `spring.datasource.hikari.connection-init-sql`, which keep the
  application and every database session on UTC

The local credentials and JWT secret are for development only. Inject environment-specific secrets in deployed environments and never commit production credentials.

The application pins its JVM default time zone to UTC before Spring starts, independent of the host
or container setting; see [Date, time, and time-zone handling](room-booking-backend/docs/features/date-time-and-time-zone-handling.md).

## Project structure

The application is a Spring Modulith modular monolith: `dev.ngb.backend` has no top-level
`model`/`repository`/`service`/`controller` packages of its own. Every persistence type and every
piece of live application code lives inside one of 22 modules, each a direct sub-package with its
own `internal/` the Spring Modulith ArchUnit test enforces other modules cannot reach into. See
[`room-booking-backend/docs/modules/README.md`](room-booking-backend/docs/modules/README.md) for the
full module index and
[`room-booking-backend/docs/architecture/modular-monolith.md`](room-booking-backend/docs/architecture/modular-monolith.md)
for why this shape exists.

```text
room-booking-backend/
├── src/main/java/dev/ngb/backend/
│   ├── platform/     # Shared kernel: idempotency, outbox, audit, the ~35-type value/enum kernel
│   ├── config/       # Security, OpenAPI, JDBC conversion, global exception handling (open module)
│   ├── market/       # Legal entity, provider account, policy bundle, localized content
│   ├── identity/     # Account holder, session, credential, capability -- the only running auth code
│   ├── hostverification/  # Seller KYC/KYB, screening, tax, payout-destination eligibility
│   ├── supply/       # Property, accommodation type, physical unit, listing, rate plan, geo catalog
│   ├── inventory/    # Availability day, hold, claim, block, iCal sync
│   ├── pricing/      # Price rule, promotion, quote, tax
│   ├── booking/      # The stay contract plus its revision, cancellation, and refund-instruction chain
│   ├── payment/      # Provider-independent payment state, webhook, refund execution, dispute gateway
│   ├── ledger/       # Double-entry accounting, host payable, payout, statement, reconciliation
│   ├── messaging/    # Conversation, message, notification intent, delivery
│   ├── stay/         # Operational stay, access grant, task, incident, evidence
│   ├── review/       # Review right, revision, publication, aspect intelligence, reputation
│   ├── trust/        # Risk signal/decision/enforcement, challenge, restriction, moderation
│   ├── support/      # Support case, evidence custody, damage claim, remedy, appeal
│   ├── discovery/    # Search/recommendation projections, ranking epoch, exposure
│   ├── analytics/    # Event/dataset contract, lineage, quality, metric, experiment
│   ├── ml/           # Feature store, label, model registry, prediction
│   ├── admin/        # Operator role, break-glass, configuration, change request, feature flag
│   ├── hostops/      # Host metric, benchmark, forecast, advice, bulk edit
│   └── growth/       # Program, referral, stored value, loyalty, campaign, affiliate
├── src/main/resources/
│   └── db/changelog/ # Liquibase migrations, 000-035 (035 is the event publication registry table)
├── docs/
│   ├── architecture/ # The modular-monolith decision and the event publication registry
│   ├── modules/      # One document per module: ownership, clusters, API, allowed dependencies
│   ├── conventions/  # The binding rule set for code changes
│   ├── data-model/   # One note per migration, plus the schema overview
│   ├── features/     # Authoritative feature designs
│   ├── learning/     # Background notes
│   └── templates/    # Document prompts
└── compose.local.yaml
```

Inside a module, the same responsibility split the codebase always used still applies, just nested
under `internal/`: `internal/web` (controllers, request/response records), `internal/service`
(business logic, factories), `internal/repository` (persistence interfaces), `internal/model`
(entities and enums, subdivided by aggregate cluster in every module large enough to need it). Only
a module's root package — never anything under `internal/` — is a legitimate target for another
module's code.

## Build and test

```bash
cd room-booking-backend
./gradlew test
./gradlew build
```

## Documentation

- [Engineering conventions — the binding rule set for all code changes](room-booking-backend/docs/conventions/README.md)
- [Modules — the binding map of Spring Modulith module ownership and boundaries](room-booking-backend/docs/modules/README.md)
- [Modular monolith architecture decision](room-booking-backend/docs/architecture/modular-monolith.md)
- [The event publication registry](room-booking-backend/docs/architecture/event-publication-registry.md)
- [Backend project foundations](room-booking-backend/docs/backend-project-foundations.md)
- [Beginner's guide](room-booking-backend/GUIDE.md)
- [Marketplace target state and implementation dependencies](room-booking-backend/docs/marketplace-problem-breakdown.md)
- [Standard feature-design document prompt](room-booking-backend/docs/templates/feature-design-document-prompt.md)
- [Data model — migration history, schema coverage, and what the schema does not yet prove](room-booking-backend/docs/data-model/README.md)
- [Platform foundation design](room-booking-backend/docs/features/platform-foundation.md)
- [Date, time, and time-zone handling design](room-booking-backend/docs/features/date-time-and-time-zone-handling.md)
- [Identity, accounts, and access design](room-booking-backend/docs/features/identity-accounts-and-access.md)
- [Global location-search design](room-booking-backend/docs/features/location-search.md)
- [Personalized search and discovery design](room-booking-backend/docs/features/personalized-discovery.md)
- [Dynamic pricing, quotes, and settlement design](room-booking-backend/docs/features/dynamic-pricing-and-settlement.md)
- [Availability, reservation, and booking lifecycle design](room-booking-backend/docs/features/availability-reservation-and-booking.md)
- [Payment orchestration design](room-booking-backend/docs/features/payment-orchestration.md)
- [Booking modification, cancellation, and refund policy design](room-booking-backend/docs/features/cancellation-modification-and-refund.md)
- [Ledger, reconciliation, and host payout design](room-booking-backend/docs/features/ledger-reconciliation-and-host-payout.md)
- [Messaging, notifications, and stay operations design](room-booking-backend/docs/features/messaging-notifications-and-stay-operations.md)
- [Trust, safety, fraud, and content moderation design](room-booking-backend/docs/features/trust-safety-fraud-and-moderation.md)
- [Disputes, damage claims, insurance, and customer support design](room-booking-backend/docs/features/disputes-damage-claims-and-support.md)
- [Reviews, aspect intelligence, and reputation design](room-booking-backend/docs/features/review-reputation-and-aspect-intelligence.md)
- [Data, experimentation, and machine-learning platform design](room-booking-backend/docs/features/data-experimentation-and-ml-platform.md)
- [Vietnam market readiness and internationalization design](room-booking-backend/docs/features/multi-market-compliance-and-localization.md)
- [Comment and documentation maintenance](room-booking-backend/GUIDE.md#18-maintaining-comments-and-documentation)
- [Commit code guide](room-booking-backend/GUIDE.md#19-committing-code)

## Database changes

Do not edit an already-applied Liquibase changeset. Add the next numbered migration under `room-booking-backend/src/main/resources/db/changelog/changes/` and include it at the end of `db.changelog-master.yaml`.
