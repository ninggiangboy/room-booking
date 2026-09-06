# Backend Project Foundations

This guide is a reusable map for engineers who are new to a backend project. It explains the responsibilities that a production-ready backend needs, how they fit together, and what to check when delivering a feature.

The Room Booking codebase supplies examples throughout the guide, but the principles apply regardless of language, framework, or business domain.

## 1. The backend at a glance

A backend receives requests, protects data and business rules, persists reliable state, and exposes an understandable contract to its clients and operators.

```text
Client
  -> API boundary (routing, DTOs, validation)
  -> Authentication and authorization
  -> Business service and transaction
  -> Persistence repository and database
  -> Response or standard error

Successful transaction
  -> domain event
  -> external side effect (email, queue, object storage, and so on)
```

Each box has one main responsibility. Keeping those boundaries clear prevents controllers from becoming business-rule containers, database code from leaking into HTTP code, and external calls from making database writes unreliable.

## 2. Bootstrap, configuration, and environments

Every backend needs a single application entry point, dependency configuration, and a safe way to supply environment-specific values.

- Keep application identity and non-secret defaults in shared configuration.
- Use profiles or equivalent environment configuration for local, test, staging, and production values.
- Inject credentials, signing keys, and third-party secrets through the deployment environment or a secret manager; never commit production values.
- Provide a repeatable local environment for dependencies such as databases, message brokers, object storage, and email capture.
- Expose a health endpoint so people and deployment systems can tell whether the application is alive.

In Room Booking, `RoomBookingBackendApplication` starts Spring Boot, `application.properties` defines shared settings, `application-local.properties` supplies development values, `compose.local.yaml` runs PostgreSQL, MinIO, and Mailpit, and `/actuator/health` provides the health check.

## 3. API boundary: controllers, DTOs, validation, and errors

The API boundary turns HTTP into an application call. It should be predictable, small, and free of business decisions.

- Define versioned, resource-oriented routes and correct HTTP status codes.
- Use separate request and response DTOs. Do not return persistence entities directly, because database structure and public API contracts evolve independently.
- Validate shape-level input at the boundary: required fields, email format, field lengths, and basic value ranges.
- Keep controllers thin: accept input, invoke one application service, and produce the response.
- Publish a machine-readable API contract, normally OpenAPI, and keep it synchronized with routes, payloads, authentication, and errors.
- Return one stable error shape. Clients should make programmatic decisions from an error code and HTTP status, never from human-readable message text.

Room Booking uses request and response records in `dto`, controllers in `controller`, Jakarta Validation through `@Valid`, OpenAPI annotations plus Swagger UI, and `ApiExceptionHandler` to return `ApiErrorResponse` consistently.

## 4. Security: identity, access, and secrets

Security is cross-cutting, but its policy must be explicit and enforced before business actions run.

- Authenticate every request that is not deliberately public.
- Authorize by current roles, permissions, ownership, and account state; do not rely only on client-provided identifiers or stale token claims.
- Hash passwords with a maintained password encoder. Store a password hash, never a raw password.
- Treat bearer tokens, reset tokens, verification tokens, API keys, and signing secrets as secrets. Redact them from logs, errors, documentation, and monitoring.
- Keep access tokens short-lived; make revocation and rotation rules explicit where sessions require them.
- Apply rate limits and enumeration-safe responses to sensitive public workflows such as login, password reset, and verification.
- Document public routes and role requirements alongside the API contract.

Room Booking's `SecurityConfig` declares public and protected routes. `JwtAuthenticationFilter` validates bearer JWTs and reloads current roles and account status. Refresh, verification, and password-reset tokens are random opaque values whose hashes, rather than raw values, are persisted.

## 5. Business logic and domain rules

The service layer owns the meaning of a feature. It is where workflows, invariants, policy decisions, and transaction boundaries belong.

- Model a use case as an application service with a clear input, output, side effects, and failure contract.
- Validate domain rules in the service even when a controller already validates the request. Services can later be called by jobs, message consumers, or other services without HTTP validation.
- Use domain-specific exceptions or result types for expected failures, then translate them once at the API boundary.
- Put all state changes that must succeed or fail together inside one transaction.
- Prefer small, reusable policy components for rules such as password strength, price calculation, availability, or cancellation eligibility.
- Keep domain behavior close to the model when it protects an invariant, but do not turn entities into containers for infrastructure code.

For example, `AuthenticationService` normalizes registration input, checks password policy, creates the account and role, and issues tokens within a transaction. `PasswordResetService` consumes the reset token, changes the password, and revokes refresh tokens atomically.

## 6. Persistence and data integrity

Persistence is more than saving objects. The database is the final authority for durable state and must enforce important invariants.

- Map domain aggregates or data models deliberately; expose database access through repository interfaces or equivalent data-access components.
- Put uniqueness, foreign-key, check, range, and exclusion constraints in the database when they protect correctness under concurrent requests.
- Normalize and validate data before querying or writing it, but retain database constraints as the final safeguard.
- Use parameterized queries; never compose SQL with untrusted values.
- Select transaction isolation, locking, and indexes based on the actual contention and query patterns.
- Use optimistic locking for normal concurrent edits, and handle a write conflict as a meaningful client-facing outcome where appropriate.
- Maintain an append-only, versioned migration history. Never alter a migration that has already been applied to a shared environment; add a forward migration instead.

Room Booking uses Spring Data JDBC models and repositories, PostgreSQL/PostGIS, `@Version` for optimistic locking, and Liquibase changesets under `src/main/resources/db/changelog`. Its schema documents important conventions such as integer minor units for money, timezone-aware timestamps, and half-open stay ranges.

## 7. Auditability and concurrency

Audit metadata answers *when* a record changed; an audit trail answers *who did what and why*. Mature systems often need both.

### Audit metadata

At minimum, persist `createdAt` and `updatedAt` in UTC for durable records. Use a shared clock so timestamps are deterministic in tests. Add a version field when concurrent updates must not silently overwrite one another.

Room Booking enables Spring Data JDBC auditing in `JdbcAuditingConfig`; `User`, `HostProfile`, `UserRole`, and `AuthToken` demonstrate creation/update timestamps and optimistic versions.

### Audit trail

For security-sensitive or financially significant actions, create an append-only audit record with:

- event ID and UTC timestamp;
- actor identity and actor role/service identity;
- action name and target resource type/ID;
- outcome, reason, and request/correlation ID when available;
- minimal safe context needed for investigation, excluding passwords, raw tokens, and unnecessary personal data.

Examples include account suspension, role grants, booking cancellation, refund decisions, payment webhook processing, and administrator configuration changes. Audit records should be access-controlled, retained according to policy, and designed so normal application users cannot modify them.

## 8. Events and external integrations

External side effects are less reliable than a local database transaction. Design them so a successful database change is never undone or falsely reported because an email, HTTP request, or queue delivery fails.

- Represent important completed state changes as domain/application events.
- Trigger external work after the transaction commits, or use an outbox pattern when reliable eventual delivery is required.
- Keep integration adapters behind interfaces so business services do not depend directly on SMTP, cloud SDKs, or vendor APIs.
- Make retry behavior, idempotency keys, timeouts, and failure handling explicit.
- Record enough safe operational context to diagnose failed delivery.

Room Booking publishes email events and handles them with `@TransactionalEventListener(AFTER_COMMIT)` in `AuthEmailNotifier`. The `EmailSender` interface separates the authentication workflow from SMTP delivery.

## 9. Documentation as a product contract

Documentation is part of the feature, not cleanup work after release.

- Maintain a README with purpose, prerequisites, local startup, important endpoints, and links to deeper documentation.
- Maintain a beginner guide explaining the architecture, conventions, and a recommended reading order.
- Keep the generated OpenAPI contract accurate for every public route.
- Document data-model decisions, invariants, migration history, and ownership of sensitive tables.
- Add purpose-level code documentation for public types and non-obvious behavior, especially security, concurrency, persistence queries, and transaction boundaries.
- Update configuration documentation whenever a property, dependency, port, secret, or operational step changes.

Room Booking keeps an API overview in `README.md`, detailed onboarding in `GUIDE.md`, data-model decisions in `docs/data-model`, OpenAPI configuration in `OpenApiConfig`, and JavaDoc close to services, repositories, DTOs, and models.

## 10. Tests and quality gates

Tests make architectural rules executable and prevent regressions as the codebase grows.

- Unit-test policies and services for success, validation, boundary, and failure cases.
- Test authorization boundaries: unauthenticated, unauthorized, suspended/deleted, owner, and administrator scenarios.
- Use focused web tests for controller routing, validation, security, and error responses.
- Use persistence/integration tests for migrations, database constraints, repository queries, transaction behavior, and concurrency-sensitive workflows.
- Test event handling and integrations with fakes or test containers; never send real email or call production services during tests.
- Run compile, tests, static checks, and documentation generation in continuous integration.

For this project, the Gradle wrapper provides `./gradlew test`, `./gradlew build`, and `./gradlew javadoc`. New features should add the smallest meaningful regression coverage at the layer where the behavior belongs.

## 11. Operations and observability

A service that cannot be understood in production cannot be safely operated.

- Provide health and readiness checks for the application and critical dependencies.
- Emit structured, searchable logs with a request/correlation ID; log failures with context but redact secrets and sensitive personal data.
- Publish metrics for request volume, latency, error rate, database behavior, background work, and important business events.
- Configure alerts from symptoms that matter to users: sustained errors, high latency, failed integrations, unhealthy dependencies, and failed migrations.
- Define backups, restore tests, data retention, and disaster-recovery ownership for persistent systems.
- Use repeatable builds and deployments, environment-specific configuration, least-privilege service accounts, and a rollback strategy.

Room Booking already exposes an actuator health endpoint and logs unexpected API failures. Structured request tracing, metrics/alerts, CI/CD, backup verification, and a full audit trail should be treated as production-readiness requirements as the service expands.

## 12. Feature delivery checklist

Use this checklist before considering a backend feature complete.

- [ ] The user outcome, API contract, status codes, and authorization rules are clear.
- [ ] Request and response DTOs are separate from persistence models; boundary validation is present.
- [ ] Business policies, expected failures, and transaction boundaries are implemented in services.
- [ ] Database constraints, indexes, migrations, and concurrency behavior preserve the invariant.
- [ ] Sensitive actions have appropriate audit metadata and, where required, append-only audit events.
- [ ] External side effects are post-commit or reliably delivered through an outbox/queue strategy.
- [ ] Logs, errors, examples, and documentation do not expose secrets or raw credentials.
- [ ] OpenAPI, README, beginner guide, configuration reference, and data-model documentation are updated.
- [ ] Tests cover the success path, invalid input, authorization boundary, persistence constraint, and relevant failure/retry behavior.
- [ ] Health, logging, metrics, deployment, backup, and rollback expectations are understood for the changed component.

## 13. Suggested reading order for a new engineer

1. Read the README and run the project locally.
2. Read `GUIDE.md` for Java/Spring concepts and the current API flow.
3. Follow one request from a controller to its service, repository, model, and migration.
4. Read `SecurityConfig`, the authentication filter, and `ApiExceptionHandler` before changing a protected endpoint.
5. Read the relevant data-model document and migration history before changing persistent data.
6. Use the feature delivery checklist for every change, even a small one.

The goal is not to add layers for their own sake. The goal is to make every feature secure, understandable, testable, operable, and safe to evolve.
