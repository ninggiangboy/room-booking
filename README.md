# Room Booking

Backend for a room-booking platform, built with Java and Spring Boot. The application currently focuses on identity and authentication; the database schema also prepares the foundation for listings, availability, bookings, payments, reviews, and favorites.

## Current features

- Guest registration and login
- JWT access tokens
- Rotating, database-backed refresh tokens
- Logout and refresh-token revocation
- Email verification and verification-email resend
- Forgot/reset password with short-lived, single-use email tokens
- Current-user lookup and password changes
- Consistent JSON error responses
- Liquibase-managed PostgreSQL schema

> The listing, calendar, booking, payment, review, and favorite schemas are present, but their Java APIs are not implemented yet.

## Tech stack

- Java 25
- Spring Boot 4.1
- Spring Security
- Spring Data JDBC
- PostgreSQL 17
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
| PostgreSQL | `localhost:5432` |
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
| `POST` | `/api/v1/auth/email-verification/resend` | Bearer token | Request another verification email |
| `POST` | `/api/v1/auth/password/forgot` | Public | Request a password-reset email |
| `POST` | `/api/v1/auth/password/reset` | Public | Reset a password with a one-time token |
| `GET` | `/api/v1/users/email-exists?email=...` | Public | Check whether an email is registered |
| `GET` | `/api/v1/users/me` | Bearer token | Get the current user |
| `PUT` | `/api/v1/users/me/password` | Bearer token | Change the current user's password |

Protected endpoints expect an access token:

```http
Authorization: Bearer <access-token>
```

## Configuration

Shared configuration is stored in `room-booking-backend/src/main/resources/application.properties`. Development defaults are in `application-local.properties` and match `compose.local.yaml`.

Important settings include:

- `spring.datasource.*` for PostgreSQL
- `spring.mail.*` for SMTP
- `app.email-verification.*` for verification links and token lifetime
- `app.password-reset.*` for reset links and token lifetime
- `security.jwt.*` for token signing and expiration

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

- [Beginner's guide](room-booking-backend/GUIDE.md)
- [Data model](room-booking-backend/docs/data-model/README.md)
- [Comment and documentation maintenance](room-booking-backend/GUIDE.md#18-maintaining-comments-and-documentation)

## Database changes

Do not edit an already-applied Liquibase changeset. Add the next numbered migration under `room-booking-backend/src/main/resources/db/changelog/changes/` and include it at the end of `db.changelog-master.yaml`.
