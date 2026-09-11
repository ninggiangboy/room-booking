# 5. API, DTOs, and errors

## Controllers

- One `@RestController` per resource, with `@RequestMapping("/api/v1/...")` at class level and
  `@RequiredArgsConstructor` for injection.
- The authenticated identity arrives as `@AuthenticationPrincipal UUID userId`. Never read the
  identity from the request body or a query parameter.
- Request bodies are `@Valid @RequestBody` records. Validation failures are converted by the global
  handler, not by the controller.
- Return the response record directly for `200`, or `ResponseEntity<Void>` with
  `ResponseEntity.noContent().build()` for `204`.
- A controller never catches a domain exception, never calls a repository, and never returns a
  persistence entity.

## Request and response records

- Requests carry Bean Validation annotations with **explicit, user-facing messages**. Validation is
  syntactic only; normalization (trimming, lowercasing) and policy (password strength) stay in the
  service.
- Responses are records that are deliberately separate from the aggregate. Returning a persistence
  entity from a controller would expose a newly added sensitive field the day someone adds one.
- A response record exposes a static `from(...)` factory that projects the aggregate and defensively
  copies any collection (`UserResponse.from(user, roles)` uses `List.copyOf`).
- Every record component is documented with an `@param` tag.

## The exception hierarchy

`exception.base` holds the abstract types that define HTTP semantics; `exception` holds the concrete
domain failures. The split exists because of `refactor: organize exception hierarchy` — the base
types are infrastructure and should not sit in the same namespace as the business errors that
extend them.

```
DomainException (code, message, immutable data map)
├── BadRequestException      → 400
│   └── ValidationException
├── UnauthorizedException    → 401
├── ForbiddenException       → 403
├── NotFoundException        → 404
├── ConflictException        → 409
└── TooManyRequestsException → 429
```

Rules:

- A concrete exception extends the abstract type matching its HTTP meaning, and **nothing decides a
  status except that type**. `ApiExceptionHandler.statusFor` switches over the abstract types only;
  a service must never set a status itself.
- Every concrete exception declares a `public static final String CODE` — a stable
  `UPPER_SNAKE_CASE` value clients match on instead of parsing messages.
- Structured context goes in the immutable `data` map, and it must be **safe to return to a
  client**: no hashes, no secrets, no internals.
- Provide a second constructor taking a `Throwable` when the same failure can be discovered two
  ways — `EmailAlreadyRegisteredException` has one for the pre-insert existence check and one for
  the unique-constraint race — so the technical cause is retained for diagnostics without changing
  the public contract.
- Never leak an infrastructure exception message into a response body.

## Error responses

All failures are rendered by the global `ApiExceptionHandler` into `ApiErrorResponse`. Security
failures that occur before the handler (`RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`)
produce the same shape, so a client parses one format.

## OpenAPI

Every endpoint carries `@Operation` with a summary and a description, and `@ApiResponses` listing
every status it can produce. Reuse the shared components (`ref = "#/components/responses/Unauthorized"`,
`ValidationError`, `AccountDisabled`, `UserNotFound`, `InternalServerError`) rather than redefining
them; define a new shared component when a response starts repeating. Mark a public endpoint with
`@SecurityRequirements` to clear the global security requirement.

## Account enumeration

Flows that could reveal whether an email belongs to an account must not. `requestReset` succeeds for
every syntactically valid email, and `UserFinder.findActiveByEmailIfPresent` returns an `Optional`
precisely so the caller can stay silent. Preserve this property when adding email-addressed flows.
