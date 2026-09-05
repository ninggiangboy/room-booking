# Repository Guidelines

## Project Structure & Module Organization

The application lives in `room-booking-backend/` and uses Java 25 with Spring Boot. Production code is under `src/main/java/dev/ngb/backend`, organized by responsibility: `controller`, shared request/response types in `dto`, `service`, `repository`, `model`, `config`, `event`, and `exception`. Runtime configuration and Liquibase migrations live in `src/main/resources`; add database changes to `db/changelog/changes/` and register them in `db.changelog-master.yaml`. API and data-model decisions are documented in `docs/`. Put tests in `src/test/java`, mirroring the production package layout. Treat `build/`, `.gradle/`, and IDE metadata as generated files.

## Build, Test, and Development Commands

Run commands from `room-booking-backend/` and use the checked-in Gradle wrapper.

- `docker compose -f compose.local.yaml up -d` starts PostgreSQL, MinIO, and Mailpit.
- `./gradlew bootRun --args='--spring.profiles.active=local'` runs the API with local service settings.
- `./gradlew test` runs the JUnit Platform test suite.
- `./gradlew build` compiles, tests, and packages the application.
- `./gradlew clean` removes generated build output.

## Coding Style & Naming Conventions

Use four-space indentation and standard Java formatting. Name classes and records in `PascalCase`, methods and fields in `camelCase`, and constants in `UPPER_SNAKE_CASE`. Keep controllers thin; place business rules in services and persistence behind repositories. Use descriptive suffixes already established in the codebase, such as `*Controller`, `*Service`, `*Repository`, `*Request`, `*Response`, and `*Exception`. Prefer constructor injection (Lombok's `@RequiredArgsConstructor` is used). Do not edit an applied Liquibase changeset; add the next numbered forward migration instead.

## Testing Guidelines

Write JUnit tests alongside each change, with names such as `AuthenticationServiceTest` or `AuthControllerTest`. Cover successful behavior, validation failures, authorization boundaries, and persistence constraints. Use Spring test slices where possible and reserve full application-context tests for cross-layer behavior. No coverage threshold is configured; prioritize meaningful regression coverage and run `./gradlew test` before submitting.

## Commit & Pull Request Guidelines

Git history is not included in this checkout. Use concise, imperative commit subjects, optionally scoped, for example `auth: rotate refresh tokens`. Keep commits focused and include migrations and documentation with the feature they support. Pull requests should explain the change, note configuration or schema impacts, link relevant issues, and include request/response examples for API changes. Add screenshots only for user-visible UI or documentation rendering changes, and report the commands used to verify the work.

## Security & Configuration

Never commit production credentials or JWT secrets. Override local defaults with environment variables or profile-specific configuration. Preserve the documented rules for token hashing, money in minor units, timestamp handling, and server-side booking validation.
