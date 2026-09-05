# Room Booking Backend: Beginner's Guide

This handbook is for a developer opening this project for the first time, including someone who is still learning Java and Spring Boot. Follow the quick start first, then use the later sections as a map while reading the code.

## 1. What this project currently does

The repository is the backend of a room-booking platform. The Java application currently implements the identity and authentication area:

- guest registration;
- login and logout;
- short-lived JWT access tokens;
- rotating, database-backed refresh tokens;
- email verification;
- forgot/reset password;
- current-user lookup and password changes;
- consistent JSON errors.

The database also contains schemas for listings, availability, bookings, payments, reviews, and favorites. Those areas are a roadmap at this stage: their Liquibase migrations exist, but their Java controllers and services do not. Do not assume that a table automatically means an API feature is implemented.

## 2. Prerequisites

Install the following tools:

- Java Development Kit (JDK) 25;
- Docker with Docker Compose;
- Git and a terminal;
- an IDE with Java support, preferably IntelliJ IDEA.

You do **not** need to install Gradle. The repository includes `gradlew`, a Gradle Wrapper that downloads and uses the expected Gradle version.

Confirm the important tools from a terminal:

```bash
java --version
docker --version
docker compose version
```

Run all commands below from the `room-booking-backend` directory.

## 3. Start the application locally

### Step 1: Start infrastructure

```bash
docker compose -f compose.local.yaml up -d
docker compose -f compose.local.yaml ps
```

The Compose file starts:

| Service | Address | Purpose |
| --- | --- | --- |
| PostgreSQL | `localhost:5432` | Persistent relational data |
| MinIO API | `http://localhost:9000` | S3-compatible storage for future image features |
| MinIO console | `http://localhost:9001` | Browser UI for local object storage |
| Mailpit SMTP | `localhost:1025` | Captures outgoing development email |
| Mailpit UI | `http://localhost:8025` | Displays captured email in a browser |

### Step 2: Run the API with the local profile

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Spring Boot starts on `http://localhost:8080` by default. Check it from another terminal:

```bash
curl http://localhost:8080/actuator/health
```

A healthy response contains `"status":"UP"`.

### Step 3: Stop the application

Press `Ctrl+C` in the terminal running Spring Boot, then stop the containers:

```bash
docker compose -f compose.local.yaml down
```

This keeps the named Docker volumes. To erase local container data as well, use `docker compose -f compose.local.yaml down --volumes` only when you intentionally want a clean database.

## 4. Configuration

`src/main/resources/application.properties` contains shared settings. `application-local.properties` overrides them when the `local` profile is active.

Important properties are:

| Property | Meaning |
| --- | --- |
| `spring.datasource.*` | PostgreSQL connection details |
| `spring.mail.*` | SMTP connection details |
| `spring.liquibase.change-log` | Root file for database migrations |
| `app.email-verification.url` | Frontend URL placed in verification email |
| `app.email-verification.token-ttl` | Verification-token lifetime |
| `app.email-verification.request-cooldown` | Minimum delay between requests from one account |
| `app.email-verification.rate-limit-window` | Rolling window used to count verification-email requests |
| `app.email-verification.rate-limit-max-requests` | Maximum requests allowed within the rolling window |
| `app.password-reset.url` | Frontend URL placed in password-reset email |
| `app.password-reset.token-ttl` | Password-reset-token lifetime |
| `security.jwt.secret` | Base64 HMAC signing key, at least 256 bits after decoding |
| `security.jwt.access-token-expiration` | JWT access-token lifetime |
| `security.jwt.refresh-token-expiration` | Refresh-token lifetime |

The local profile contains development-only credentials. Never reuse them in a deployed environment. Supply secrets through environment-specific configuration and never commit production credentials.

## 5. Project structure

```text
src/main/java/dev/ngb/backend
├── config/          Spring configuration, security filter, and API error handlers
├── controller/      HTTP endpoints
├── dto/             Shared request and response data-transfer objects
├── event/           Internal application events
├── exception/       Business failures with stable error codes
├── model/           Spring Data JDBC entities and enums
├── repository/      Database access interfaces
├── service/         Business workflows, validation, tokens, accounts, and email
└── util/            Shared stateless helpers such as string normalization

src/main/resources
├── application.properties
├── application-local.properties
└── db/changelog/    Liquibase migration history
```

The usual dependency direction is:

```text
HTTP request
    -> security filter
    -> controller
    -> service
    -> repository
    -> PostgreSQL

DomainException
    -> ApiExceptionHandler
    -> JSON error response
```

Controllers should stay thin. Spring binds HTTP bodies to request DTOs, which controllers pass intact to services; services own validation and business rules and return response DTOs. Repositories isolate persistence operations.

## 6. Java concepts used in this codebase

### Classes and objects

A class defines state and behavior. `User` is a class whose objects represent rows in the `users` table. Fields hold state; methods such as `isActive()` express behavior.

### Interfaces

An interface describes a contract without choosing an implementation. `EmailSender` says that email can be sent. `SmtpEmailSender` implements that contract using Spring Mail. Repository interfaces are implemented automatically by Spring Data.

### Records

A record is a compact immutable data carrier. This declaration:

```java
public record EmailExistsResponse(boolean exists) {
}
```

is approximately equivalent to a final class with:

- a private final `exists` field;
- a constructor accepting `exists`;
- an `exists()` accessor;
- value-based `equals()` and `hashCode()`;
- a readable `toString()`.

It does not generate a JavaBean-style `getExists()` method. For a component named `email`, call `request.email()`.

Request and response types such as `LoginRequest` and `AuthResponse` are records because boundary data should not change after creation. Request DTOs end in `Request`; response DTOs end in `Response`. Records are not used for Spring Data JDBC entities here because those objects need setters while the persistence framework and services update their state.

Annotations may be placed directly on record components:

```java
public record VerifyEmailRequest(
        @NotBlank(message = "token must not be blank") String token) {
}
```

Jackson calls the generated canonical constructor while reading JSON. Spring runs the component constraint when the controller marks the request with `@Valid`.

### Enums

An enum defines a fixed set of named values. `Role` can be `GUEST`, `HOST`, or `ADMIN`; `UserStatus` describes the account lifecycle.

### Annotations

Annotations are metadata. They do nothing merely because an `@` symbol exists: a compiler, framework, annotation processor, or runtime component must interpret them.

#### Spring component annotations

| Annotation | Meaning in this project |
| --- | --- |
| `@SpringBootApplication` | Enables Boot configuration and scans `dev.ngb.backend` descendants |
| `@Configuration` | Marks a class whose methods declare application configuration |
| `@Bean` | Registers one method's returned object in Spring's application context |
| `@Component` | Registers a general-purpose Spring-managed object |
| `@Service` | Registers a component specifically representing business/application logic |
| `@RestController` | Registers an MVC controller and serializes return values as JSON |
| `@RestControllerAdvice` | Applies exception handlers to all REST controllers |

`@Component` and `@Service` have similar runtime registration behavior. The specialized name communicates architectural intent to readers.

#### HTTP and validation annotations

| Annotation | Meaning in this project |
| --- | --- |
| `@RequestMapping` | Adds the common URL prefix for a controller |
| `@GetMapping`, `@PostMapping`, `@PutMapping` | Binds one controller method to an HTTP method and path |
| `@RequestBody` | Deserializes JSON into the annotated parameter |
| `@RequestParam` | Reads a query-string parameter |
| `@AuthenticationPrincipal` | Injects the user UUID created by the JWT filter |
| `@Valid` | Triggers Jakarta Bean Validation for the nested request record |
| `@NotBlank` | Rejects null, empty, and whitespace-only text |
| `@Email` | Checks basic email-address syntax |
| `@Size` | Checks a string/collection size boundary |
| `@ExceptionHandler` | Routes selected exception types to one response method |

Validation annotations protect the HTTP boundary. Service policies still protect business invariants because a service may later be called from a scheduled job, message consumer, or another service without MVC.

#### Persistence and transaction annotations

| Annotation | Meaning in this project |
| --- | --- |
| `@Table("users")` | Maps a class to a database table |
| `@Id` | Marks the property used as the row's primary identifier |
| `@Version` | Enables optimistic locking against concurrent updates |
| `@Query` | Supplies explicit repository SQL instead of name-derived SQL |
| `@Param` | Binds a Java method argument to a named SQL parameter |
| `@Transactional` | Runs the method inside a database transaction |
| `@TransactionalEventListener(AFTER_COMMIT)` | Invokes the listener only after a successful commit |

Spring applies `@Transactional` through a proxy around the bean. Calls entering the bean through that proxy receive transaction behavior. A private method or a self-call inside the same object should not be treated as a new transaction boundary.

#### Other annotations

- `@Value("${property.name}")` injects an external configuration property.
- `@Override` makes the compiler verify that a method implements or replaces a parent declaration.
- `@NonNull` communicates nullness expectations to tools and callers.
- `@WebMvcTest`, `@Import`, `@MockitoBean`, `@Autowired`, and `@Test` assemble and run the focused MVC security test.

### Lombok

Lombok is a compile-time annotation processor. The source stays short, but generated members exist in compiled bytecode. IDE Lombok support should be enabled so navigation and autocomplete understand them.

| Lombok annotation | Generated code | Why it is used |
| --- | --- | --- |
| `@Getter` | Getter for each field | Lets persistence models expose state without handwritten boilerplate |
| `@Setter` | Setter for each non-final field | Allows services and Spring Data to update mutable entities |
| `@Builder` | Fluent builder API and builder class | Makes entity construction readable and order-independent |
| `@Builder.Default` | Preserves a field initializer when using the builder | Prevents builder-created defaults from becoming null/zero unexpectedly |
| `@RequiredArgsConstructor` | Constructor for final and required fields | Implements constructor dependency injection |
| `@NoArgsConstructor` | Zero-argument constructor | Allows persistence tooling to instantiate an entity |
| `@AllArgsConstructor` | Constructor containing every field | Supports builders/framework construction |
| `@EqualsAndHashCode` | Value-based equality and hash code | Makes `UserRoleId` behave as a composite identifier value |
| `@Slf4j` | Static SLF4J field named `log` | Provides logging without declaring the field manually |

For example:

```java
@RequiredArgsConstructor
public class ExampleService {
    private final UserRepository userRepository;
}
```

behaves as if this constructor had been written:

```java
public ExampleService(UserRepository userRepository) {
    this.userRepository = userRepository;
}
```

Lombok should remove mechanical code, not hide business behavior. Validation, token rules, and domain methods remain handwritten and documented.

### JavaDoc

JavaDoc comments start with `/**`, unlike ordinary implementation comments that start with `//`. A JavaDoc belongs immediately before the class, record, enum, field, constructor, or method it documents.

Common tags used here are:

- `@param name` — explains an input parameter or record component;
- `@return` — explains the returned value and important null/empty behavior;
- `@throws` — documents meaningful failure contracts;
- `{@code ...}` — formats code safely inline;
- `{@link Type}` — creates a link to another documented Java symbol.

Generate browsable HTML documentation with:

```bash
./gradlew javadoc
```

Open `build/docs/javadoc/index.html` afterward. Document public contracts and non-obvious framework behavior; use normal comments inside methods only to explain why an implementation choice exists.

### Generics

Types inside angle brackets describe the contained value. `Optional<User>` may contain one user or be empty. `List<Role>` contains roles. `ListCrudRepository<User, UUID>` manages `User` objects identified by `UUID` values.

### Exceptions

An exception interrupts normal execution when a request cannot succeed. Application failures extend `DomainException`, which carries a stable code and safe structured data. `ApiExceptionHandler` converts these exceptions into HTTP responses.

### Visibility

- `public` members are accessible from other packages.
- `private` members are implementation details of one class.
- no modifier means package-private access. This project uses package-private helpers such as token issuance to limit which collaborators may call them.

## 7. Spring Boot concepts in this project

### Application startup and component scanning

`RoomBookingBackendApplication` contains the entry point. `@SpringBootApplication` enables auto-configuration and scans its package and child packages for Spring components.

### Dependency injection

Spring creates registered objects, called beans, and supplies their dependencies. Most classes use Lombok's `@RequiredArgsConstructor`, which generates a constructor for `final` fields. Constructor injection makes dependencies explicit and allows tests to replace them.

### MVC and JSON

Spring MVC maps a route such as `POST /api/v1/auth/login` to a controller method. `@RequestBody` converts JSON into a Java record. Returning a record causes it to be serialized back to JSON.

### Spring Data JDBC

Spring Data JDBC maps aggregate objects directly to relational tables. It is not JPA/Hibernate: there is no lazy loading or persistence session. Repository names such as `findByEmail` can generate simple SQL automatically. `@Query` is used when an explicit query is clearer.

The `@Version` field enables optimistic locking. An update includes the previously read version so concurrent changes are detected instead of silently overwriting each other.

Repository method names form a small query language:

| Java method fragment | SQL meaning |
| --- | --- |
| `findBy` | Select matching rows |
| `existsBy` | Return whether at least one matching row exists |
| `And` | Join two predicates with `AND` |
| `IsNull` | Generate an `IS NULL` predicate |
| `OrderByRole` | Add `ORDER BY role` |

For example, `findAllByUserIdAndTypeAndConsumedAtIsNull(userId, type)` is conceptually:

```sql
SELECT ...
FROM auth_tokens
WHERE user_id = ?
  AND type = ?
  AND consumed_at IS NULL;
```

Spring binds method arguments to placeholders, so values are not concatenated into the SQL text. Exact selected columns and dialect-specific existence queries are framework implementation details. Each declared repository method contains its corresponding pseudo-SQL in JavaDoc; `UserRoleRepository` shows the exact SQL supplied through `@Query`.

### Transactions

`@Transactional` makes a service method one unit of database work. If an unchecked exception escapes, Spring rolls back the transaction. `readOnly = true` documents query-only work and may allow database optimizations.

Email delivery is intentionally triggered with an `AFTER_COMMIT` event listener. A user never receives a verification token for a database transaction that later rolls back.

### Spring Security

The API is stateless; it does not create a server session. `JwtAuthenticationFilter` reads `Authorization: Bearer <token>`, verifies the JWT signature and expiration, then stores the user ID and roles in Spring's `SecurityContext` for the current request.

Public endpoints are explicitly listed in `SecurityConfig`. Every other endpoint requires authentication. Missing authentication produces `401`; an authenticated user without sufficient authority produces `403`.

### Liquibase

Liquibase applies versioned database changes on startup. `db.changelog-master.yaml` includes changesets in order. Never edit a changeset that has been applied to an environment: Liquibase records its checksum. Add the next numbered forward migration instead.

## 8. Authentication design

### Passwords

Raw passwords must contain at least eight characters, including an uppercase letter, a lowercase letter, a number, and a special character. Valid passwords are passed to Spring Security's delegating password encoder, and only a password hash is stored. The 72-byte limit exists because BCrypt truncates longer input; Java characters and UTF-8 bytes are not always the same size.

### Access tokens

An access token is a signed JWT containing the user ID as `sub`, plus email and roles. It is short-lived and is not stored in the database. The server verifies its signature and expiration on every protected request.

### Opaque one-time tokens

Refresh, email-verification, and password-reset tokens are random opaque secrets. The client receives the raw token once, while the database stores only its SHA-256 hash. A leaked database therefore does not immediately reveal usable raw tokens. `SecureTokenUtils` owns secure generation, `HashUtils` owns hashing, and `AuthToken.isUsableAt` centralizes the consumed/expired check.

Refresh tokens rotate: refreshing consumes the old token and returns a new one. Replaying the old value fails. Issuing a new email-verification token consumes older unconsumed verification tokens.

Password recovery deliberately returns the same `204 No Content` response for registered and unregistered valid emails, preventing account enumeration. A successful reset consumes its one-time token and revokes every unconsumed refresh token for that user. Previously issued access JWTs are stateless and remain valid only until their short expiration.

## 9. Try the complete API flow

Keep the API running with the local profile while executing these commands.

### Register

```bash
curl --request POST http://localhost:8080/api/v1/auth/register \
  --header 'Content-Type: application/json' \
  --data '{
    "email": "beginner@example.com",
    "password": "Correct-Horse1!",
    "displayName": "Beginner Developer"
  }'
```

The response status is `201 Created` and the body contains `accessToken`, `refreshToken`, and `user`. Registration does not send an email. Copy both tokens for later steps.

### Request an email-verification link

The authenticated user starts verification explicitly. Requests have a configurable cooldown and
rolling-window quota; exceeding either limit returns `429 Too Many Requests` with `retryAt` and
`retryAfterSeconds` in the error data.

```bash
curl --request POST http://localhost:8080/api/v1/auth/email-verification/request \
  --header 'Authorization: Bearer PASTE_ACCESS_TOKEN_HERE'
```

### Verify email

Open `http://localhost:8025`, select the verification message, and copy the `token` query parameter from its link. Then run:

```bash
curl --request POST http://localhost:8080/api/v1/auth/email-verification/confirm \
  --header 'Content-Type: application/json' \
  --data '{"token":"PASTE_EMAIL_TOKEN_HERE"}'
```

The returned user now has a non-null `emailVerifiedAt` value.

### Login

```bash
curl --request POST http://localhost:8080/api/v1/auth/login \
  --header 'Content-Type: application/json' \
  --data '{
    "email": "beginner@example.com",
    "password": "Correct-Horse1!"
  }'
```

Copy the new access and refresh tokens. In the examples below, replace placeholders with the actual values; do not include angle brackets.

### Read the current user

```bash
curl http://localhost:8080/api/v1/users/me \
  --header 'Authorization: Bearer PASTE_ACCESS_TOKEN_HERE'
```

### Rotate the refresh token

```bash
curl --request POST http://localhost:8080/api/v1/auth/refresh \
  --header 'Content-Type: application/json' \
  --data '{"refreshToken":"PASTE_REFRESH_TOKEN_HERE"}'
```

Save the new refresh token. The token used in this request has been consumed and cannot be used again.

### Change the password

```bash
curl --request PUT http://localhost:8080/api/v1/users/me/password \
  --header 'Authorization: Bearer PASTE_ACCESS_TOKEN_HERE' \
  --header 'Content-Type: application/json' \
  --data '{
    "currentPassword": "Correct-Horse1!",
    "newPassword": "New-Correct-Horse2!"
  }'
```

Successful password changes return `204 No Content`.

### Logout

Use the latest refresh token:

```bash
curl --request POST http://localhost:8080/api/v1/auth/logout \
  --header 'Content-Type: application/json' \
  --data '{"refreshToken":"PASTE_LATEST_REFRESH_TOKEN_HERE"}'
```

Logout returns `204 No Content`. It is idempotent: sending the same request again does not fail, but that token can no longer refresh the session.

### Recover a forgotten password

Requesting recovery is public and always returns `204 No Content` for a syntactically valid email, whether the account exists or not:

```bash
curl --request POST http://localhost:8080/api/v1/auth/password/forgot \
  --header 'Content-Type: application/json' \
  --data '{"email":"beginner@example.com"}'
```

Open Mailpit at `http://localhost:8025`, select the password-reset message, and copy the `token` query parameter. Consume it with a password that satisfies the project policy:

```bash
curl --request POST http://localhost:8080/api/v1/auth/password/reset \
  --header 'Content-Type: application/json' \
  --data '{
    "token": "PASTE_PASSWORD_RESET_TOKEN_HERE",
    "newPassword": "Recovered-Password3!"
  }'
```

A successful reset returns `204 No Content`. The token is single-use, and all existing refresh tokens for the account are revoked. Log in with the new password to obtain a new token pair.

### Check whether an email exists

```bash
curl 'http://localhost:8080/api/v1/users/email-exists?email=beginner%40example.com'
```

## 10. Error responses

Business and validation failures use one JSON shape:

```json
{
  "timestamp": "2026-01-01T00:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "email is invalid",
  "data": {
    "field": "email"
  },
  "path": "/api/v1/auth/register"
}
```

Use `status` for HTTP handling, `code` for stable program logic, `message` for a readable explanation, and `data` for structured context. Do not make clients depend on exact message wording.

## 11. Database migration map

The existing numbered SQL files are historical, ordered changesets:

| Phase | Main result |
| --- | --- |
| `000` | PostgreSQL extensions for UUIDs, case-insensitive text, and range constraints |
| `001` | Users, user roles, and host profiles |
| `002` | Listings, images, amenities, and search indexes |
| `003` | Per-day availability and pricing |
| `004` | Bookings, immutable nightly snapshots, and overlap protection |
| `005` | Payment attempts, refunds, and idempotent webhook events |
| `006` | Reviews and favorites |
| `007` | Initial email-verification tokens |
| `008` | Generalized authentication tokens, including refresh tokens |
| `009` | Password-reset token type added to the authentication-token constraint |

Important data conventions are documented in `docs/data-model/README.md`: money uses integer minor units, stay ranges are half-open, timestamps use timezone-aware values, and deletion is normally represented by status rather than removing historical rows.

## 12. Recommended reading order

1. `RoomBookingBackendApplication` to see startup.
2. `AuthController` and its request/response records to see the HTTP surface.
3. `SecurityConfig` and `JwtAuthenticationFilter` to understand public and protected routes.
4. `AuthenticationService` to follow the main use cases.
5. `User`, `AuthToken`, and their repositories to connect Java objects to tables.
6. `EmailVerificationService`, `PasswordResetService`, and `AuthEmailNotifier` to see reusable token utilities, transactions, and post-commit events.
7. `UserFinder` and `UserAccountPolicy` to see shared domain behavior extracted from workflows.
8. `ApiExceptionHandler` and the exception package to understand failures.
9. The Liquibase master file and `docs/data-model/README.md` to understand the broader roadmap.

Use the IDE's “Go to declaration” action whenever an annotation, method, or type is unfamiliar.

## 13. Debugging and tests

Run the automated suite:

```bash
./gradlew test
```

Run compilation, tests, and packaging together:

```bash
./gradlew build
```

In IntelliJ IDEA, import the directory as a Gradle project, select JDK 25, create a Spring Boot run configuration for `RoomBookingBackendApplication`, and add the active profile `local`. Put breakpoints in a controller and the service it calls, then send a `curl` request and step through the flow.

When debugging authentication, inspect these values in order:

1. the incoming `Authorization` header;
2. claims returned by `AccessTokenService`;
3. the authentication stored in `SecurityContextHolder`;
4. the `@AuthenticationPrincipal UUID` controller argument.

Never print raw passwords, JWT signing secrets, refresh tokens, verification tokens, or password-reset tokens in application logs.

## 14. Adding a small feature safely

For example, to add a display-name update:

1. define a request record in `dto`;
2. add a protected route to `UserController`;
3. validate the request DTO and normalize text through `StringUtils`;
4. implement the transactional update in `UserAccountService`;
5. save through `UserRepository` and update `updatedAt`;
6. add controller/security and service tests;
7. document the endpoint and run `./gradlew test` and `./gradlew build`.

If the feature changes the database, add a new numbered Liquibase file and include it after the existing changesets. Never rewrite migration history.

## 15. Troubleshooting

### The API cannot connect to PostgreSQL

Confirm Docker is running, execute `docker compose -f compose.local.yaml ps`, and check that PostgreSQL is healthy on port `5432`. Make sure the `local` Spring profile is active.

### Port 5432, 8080, 8025, 9000, or 9001 is already in use

Stop the conflicting process or change the local port mapping. If a Compose host port changes, update the matching application property or URL you use locally.

### Startup says the JWT secret is blank or too short

The shared configuration intentionally has no secret. Start with the `local` profile or provide a Base64-encoded secret containing at least 32 decoded bytes.

### Verification email does not arrive

Check that Mailpit is running, open `http://localhost:8025`, and inspect the application log. Email is sent after the token transaction commits.

### Password-reset email does not arrive

The endpoint intentionally does not reveal whether an account exists. Confirm the submitted email belongs to an active local account, then check Mailpit and the application log. Requesting another reset invalidates the previous unconsumed reset token.

### A protected request returns 401

Confirm the header starts with `Bearer `, the access token has not expired, and you did not accidentally pass a refresh token. Refresh tokens are not JWT access tokens.

### Liquibase reports a checksum mismatch

An applied changeset was probably edited. Restore that historical file and create a new forward changeset for the intended database change.

## 16. Glossary

- **API**: the HTTP interface used by clients.
- **Bean**: an object created and managed by Spring.
- **DTO**: a data-transfer object used at a boundary such as JSON input or output.
- **JWT**: a signed token whose claims can be verified without a database lookup.
- **Opaque token**: a random value with no client-readable meaning.
- **Hash**: a one-way digest used here to avoid storing raw token secrets.
- **Aggregate**: a persistence unit managed through one root object in Spring Data JDBC.
- **Transaction**: a group of database operations that commit or roll back together.
- **Migration**: a versioned database schema change.
- **Optimistic locking**: version-based detection of conflicting concurrent updates.
- **Idempotent**: safe to repeat without producing an additional effect.

## 17. Beginner exercises

1. Trace a login request from `AuthController` to the repository and back.
2. Submit an invalid email and find where `VALIDATION_ERROR` is created and mapped.
3. Call `/api/v1/users/me` without a token, with an invalid token, and with a valid token; compare responses.
4. Refresh once, then try the old refresh token again and explain why it fails.
5. Replace the application `Clock` with a fixed clock in a unit test and test token expiration.
6. Draw the tables touched during registration and mark the transaction boundary.
7. Request two password-reset emails and explain why only the newest token remains usable.
8. Reset a password and verify that an older refresh token can no longer create a session.

These exercises cover the project's central ideas without requiring a new production feature.

## 18. Maintaining comments and documentation

Documentation is part of the definition of done. Review it in the same change as production code so names, routes, configuration, and security behavior cannot drift.

### JavaDoc rules

- Add type-level JavaDoc to every new class, interface, record, enum, and annotation. State its responsibility, architectural layer, and important Spring/Lombok behavior.
- Document public methods and package-private business entry points with purpose, side effects, transaction/security behavior, `@param`, `@return`, and meaningful `@throws` tags.
- For request/response records, add an `@param` for every record component. Explain `@Valid`, `@NotBlank`, `@Email`, or `@Size` when validation behavior is not obvious.
- For Lombok types, document what the annotations generate and why. Do not write fictional constructors or getters that would duplicate Lombok-generated code.
- Document enum constants when their business meaning is not completely obvious. Document security-sensitive fields such as hashes, raw-token boundaries, expiry, consumption, and optimistic-lock versions.
- For every declared repository method, include conceptual generated SQL in a `<pre>{@code ...}</pre>` block, parameter binding, return-cardinality semantics, and how Spring parses the method name. For `@Query`, copy and explain the exact query.
- Use `//` implementation comments only for the reason behind a non-obvious decision, such as race protection, token hashing, or post-commit failure handling. Do not narrate straightforward assignments or method calls.
- Never place passwords, signing secrets, raw bearer tokens, production credentials, or real personal data in comments or examples.

### Documentation update map

When a change affects any item in the left column, update the corresponding documentation in the same commit:

| Code change | Required documentation |
| --- | --- |
| Public endpoint, authentication, or payload | Root `README.md` API table and runnable flow in `GUIDE.md` |
| New configuration property or local port | Properties comments, README configuration, GUIDE setup/configuration/troubleshooting |
| New package or major component | Package `package-info.java`, project tree, and recommended reading order |
| Token, password, transaction, or security behavior | JavaDoc plus GUIDE design/security explanation |
| New database migration | Changelog master, migration map, and relevant data-model document |
| Renamed or removed type | Search README, GUIDE, JavaDoc links, tests, and examples for the old name |

Never edit comments inside an already-applied Liquibase changeset merely to improve prose, because changing historical files can affect checksum validation. Explain historical migrations in the GUIDE or data-model docs; document new SQL while creating its new changeset.

### Pre-commit documentation check

From the repository root, first inspect the complete change surface:

```bash
git status --short
git diff --name-status
git diff --check
```

Search both tracked changes and untracked Java files for files that contain no JavaDoc at all:

```bash
{
  git diff --name-only --diff-filter=AM -- '*.java'
  git ls-files --others --exclude-standard -- '*.java'
} | sort -u | while IFS= read -r file; do
  if test -f "$file" && ! rg -q '/\*\*' "$file"; then
    echo "Review JavaDoc: $file"
  fi
done
```

This command is only a first-pass guard: manually verify every new type, record component, public method, repository method, and non-obvious contract using the rules above. Remember that normal `git diff` does not display untracked file contents until they are added to Git.

Finally generate and verify all artifacts:

```bash
cd room-booking-backend
./gradlew test
./gradlew build
./gradlew javadoc
```

Open `build/docs/javadoc/index.html` and follow links for newly documented types. Treat new missing-member, missing-tag, invalid-link, or malformed-HTML warnings as documentation defects. Warnings that mention only implicit or Lombok-generated constructors are non-functional and should not be “fixed” by adding duplicate boilerplate constructors; verify instead that the type-level JavaDoc explains how construction and dependency injection work.
