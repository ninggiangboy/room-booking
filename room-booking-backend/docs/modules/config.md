# `config`

## Goal

Give Spring's cross-cutting wiring — security, OpenAPI, JDBC conversion, JDBC auditing, the global
exception handler — a home that is allowed to depend on anything, instead of letting it default into
two separate, illegitimate auto-detected modules.

## Forces that shaped it

Spring Modulith treats every direct sub-package of `dev.ngb.backend` as a module automatically, with
no opt-in required. Today `config/` and `filter/` are two such sub-packages, so leaving them as-is
would silently create two modules — `config` and `filter` — each of which imports from other
modules' internals (`JwtAuthenticationFilter` reaches into an `identity` service; `JdbcConversionConfig`
reaches into `platform` value types) with no declared right to do so. `ApplicationModules.verify()`
would fail on both the moment real module boundaries exist elsewhere.

Declaring one `config` module with `@ApplicationModule(type = Type.OPEN)` turns "config may depend
on anything," already a rule in
[`../conventions/01-architecture-and-layering.md`](../conventions/01-architecture-and-layering.md),
into something `verify()` can actually check rather than a rule enforced only by review.

## What it owns

- `SecurityConfig`, `RestAccessDeniedHandler`, `RestAuthenticationEntryPoint` — Spring Security
  wiring.
- `JwtAuthenticationFilter` — moved in from the current top-level `filter/` package.
- `ApiExceptionHandler` — the global `@ControllerAdvice`, also moved in from `filter/`.
- `OpenApiConfig` — the springdoc/OpenAPI contract.
- `JdbcConversionConfig` — Spring Data JDBC custom converters, including the ones that read
  `platform`'s `JsonDocument`/`StayRange`/`BucketRange`.
- `JdbcAuditingConfig`, `TimeConfig` — auditing wiring and the application `Clock` bean.

`filter/` disappears as a top-level package; its two classes move into `config/` in the same commit
that adds `config`'s `package-info.java`, because merging is what removes the illegitimate
auto-detected `filter` module — declaring `config` alone, with `filter` left where it is, would not
fix anything.

## Aggregate clusters inside it

None — `config` owns no persistent aggregates. It stays a flat package of Spring `@Configuration`
classes and the two servlet-filter-adjacent types, the same shape it has today.

## What it does not own

Any business rule. `ApiExceptionHandler` maps exception types to HTTP responses; it must not decide
what response an exception deserves beyond what the exception's own type already encodes (see
[`../conventions/05-api-dto-and-errors.md`](../conventions/05-api-dto-and-errors.md)).

## Public API

Everything in `config` is public by necessity — it is Spring configuration, discovered by component
scan, not called directly by other modules' code.

## Allowed dependencies

Everything. `config` is the one module besides `platform` given a non-default `type` —
`Type.OPEN` — specifically so that `SecurityConfig`'s route table, `JwtAuthenticationFilter`'s call
into `identity`'s public `AccessTokenService` (see
[`identity.md`](identity.md#what-changes-for-jwtauthenticationfilter)), and `JdbcConversionConfig`'s
import of `platform` value types are all legitimate rather than violations to suppress.

## Data coupling `verify()` cannot see

None — `config` owns no tables, so there is no schema-level coupling to record.

## What it leaves open

`SecurityConfig` remains the one place that names every route as a string pattern. It does not
import any controller or service, so splitting modules does not by itself create a new dependency
here — but it does mean no module can yet say "these are my routes" the way it can say "this is my
public API." Moving to a per-module `Customizer<AuthorizeHttpRequestsConfigurer...>` contribution is
a real improvement and explicitly out of scope for this migration; see
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md).

## Exit criteria

- `filter/` no longer exists as a top-level package; both its classes are `config`'s.
- `config` carries `package-info.java` with `@ApplicationModule(type = Type.OPEN)` and
  `@NullMarked`.
- `ApplicationModules.verify()` raises no violation for anything `config` imports.
