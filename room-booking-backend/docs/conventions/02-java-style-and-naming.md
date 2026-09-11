# 2. Java style and naming

## Formatting

Four-space indentation, standard Java formatting, no tabs, no trailing whitespace (`git diff --check`
must be clean). Wrap long parameter lists one parameter per line, as the services already do.

## Naming

- Classes and records in `PascalCase`, methods and fields in `camelCase`, constants in
  `UPPER_SNAKE_CASE`.
- Keep the established suffixes: `*Controller`, `*Service`, `*Repository`, `*Factory`, `*Request`,
  `*Response`, `*Exception`, `*Policy`, `*Config`, `*Utils`.
- A name describes the responsibility, not the mechanism: `UserFinder`, `PasswordPolicy`,
  `StayCalendar`, `AuthTokenFactory`.
- Stable API error codes are `UPPER_SNAKE_CASE` string constants named `CODE` on the exception that
  owns them.

## Java version and modern language features

The toolchain is Java 25. Use the modern constructs the codebase already relies on:

- **Records** for every immutable carrier: requests, responses, events, and the small bundles a
  factory returns (`NewUser`, `IssuedToken`).
- **Pattern-matching `switch`** over sealed-ish hierarchies, as `ApiExceptionHandler.statusFor`
  maps an exception type to an HTTP status.
- **The unnamed pattern and unnamed variable `_`** for a binding or a caught exception that is
  deliberately unused. Do not name it `ignored`.
- **Text blocks** for embedded SQL or multi-line literals.

## Constructor injection only

Never use field injection. Dependencies are `private final` fields populated by a constructor:

- Prefer Lombok's `@RequiredArgsConstructor`.
- Write the constructor explicitly when it must **validate** an injected value or read a
  `@Value` property. `PasswordResetService` does this so a misconfigured TTL fails at startup
  (`DurationUtils.requirePositive(tokenTtl, "password reset token TTL")`) rather than issuing
  tokens that never expire.

## Lombok

Lombok is used deliberately and narrowly:

- Aggregates in `model` use `@Getter @Setter @Builder @NoArgsConstructor(access = PROTECTED)
  @AllArgsConstructor` — the protected no-args constructor exists for Spring Data, not for callers.
- Components use `@RequiredArgsConstructor`.
- `@Getter` on an exception exposes its `code` and `data`.

Do not add Lombok annotations beyond what the type needs, and never hand-write a constructor or
accessor that duplicates a generated one. Document at type level what the annotations generate and
why — see [06](06-documentation-and-javadoc.md).

## Nullness

Nullness is part of the public contract. Packages are annotated `@org.jspecify.annotations.NullMarked`
in their `package-info.java`, which makes every unannotated type non-null. A value that may be
absent is marked `@Nullable` at its declaration:

```java
private @Nullable Instant consumedAt;
```

Return `Optional` from lookups that may find nothing; return an empty collection, never `null`, from
a query that may match nothing. Defensively copy collections that cross an immutability boundary
(`List.copyOf`, `Map.copyOf`) so a response or an exception payload cannot be mutated after
construction.

## Stateless utilities

`util` holds `final`-by-convention helpers with static methods and no Spring dependency:
`HashUtils.sha256Hex`, `SecureTokenUtils.generateUrlSafe`, `StringUtils.normalizeLowerCase`,
`DurationUtils.requirePositive`. Put a helper here only when it is genuinely stateless and reusable;
anything that needs a collaborator or a clock is a component, not a utility.

Pure functions on a domain primitive stay static for the same reason: after the time refactor, every
`StayCalendar` method that does not need the current instant is `static`, so only `now()` and
`today(ZoneId)` require the injected clock.
