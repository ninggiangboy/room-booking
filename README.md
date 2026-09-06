# Room Booking

Backend for a room-booking platform, built with Java and Spring Boot. The application currently focuses on identity and authentication; the database schema also prepares the foundation for listings, availability, bookings, payments, reviews, and favorites.

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

> The listing, calendar, booking, payment, review, and favorite schemas are present, but their Java APIs are not implemented yet.

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
- MinIO for future object-storage features

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

The local credentials and JWT secret are for development only. Inject environment-specific secrets in deployed environments and never commit production credentials.

## Project structure

```text
room-booking-backend/
├── src/main/java/dev/ngb/backend/
│   ├── config/       # Security and application configuration
│   ├── controller/   # REST endpoints
│   ├── dto/          # Request and response types
│   ├── event/        # Application events
│   ├── exception/    # Domain and API errors
│   ├── model/        # Spring Data JDBC entities
│   ├── repository/   # Persistence interfaces
│   ├── service/      # Business logic
│   └── util/         # Shared normalization, duration, hashing, and token helpers
├── src/main/resources/
│   └── db/changelog/ # Liquibase migrations
├── docs/data-model/  # Data-model documentation
└── compose.local.yaml
```

## Build and test

```bash
cd room-booking-backend
./gradlew test
./gradlew build
```

## Documentation

- [Backend project foundations](room-booking-backend/docs/backend-project-foundations.md)
- [Beginner's guide](room-booking-backend/GUIDE.md)
- [Data model](room-booking-backend/docs/data-model/README.md)
- [Global location-search design](room-booking-backend/docs/features/location-search.md)
- [Location-search schema](room-booking-backend/docs/data-model/010-location-search.md)
- [Comment and documentation maintenance](room-booking-backend/GUIDE.md#18-maintaining-comments-and-documentation)
- [Commit code guide](room-booking-backend/GUIDE.md#19-committing-code)

## Database changes

Do not edit an already-applied Liquibase changeset. Add the next numbered migration under `room-booking-backend/src/main/resources/db/changelog/changes/` and include it at the end of `db.changelog-master.yaml`.
