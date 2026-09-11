# 1. Architecture and layering

## Package map

Production code lives under `dev.ngb.backend`, organized by responsibility rather than by feature:

| Package | Contains | May depend on |
| --- | --- | --- |
| `controller` | HTTP entry points, one per resource | `dto`, `service` |
| `dto` | Request and response records shared across the HTTP boundary | `model` (for projections only) |
| `service` | Use cases, business rules, factories, policies | `dto`, `model`, `repository`, `event`, `time`, `util`, `exception` |
| `repository` | Spring Data JDBC interfaces | `model` |
| `model` | Persistence aggregates and domain enums | nothing in the application |
| `time` | Calendar and time-zone primitives | nothing in the application |
| `exception` | Domain failures; `exception.base` holds the abstract HTTP-mapped types | nothing in the application |
| `filter` | Servlet filters and the global exception handler | `dto`, `exception`, `service` |
| `config` | Spring configuration and security wiring | anything |
| `event` | Application events published for post-commit work | nothing in the application |
| `util` | Stateless helpers with no Spring dependency | nothing in the application |

The dependency direction is one-way: `controller → service → repository → model`. A repository must
never be injected into a controller, and `model`, `time`, `util`, and `event` must never import from
`service` or above.

## Service subpackages

`service` is divided by capability — `service.auth`, `service.account`, `service.host`,
`service.mail`, `service.user`, `service.validation`. A new capability gets its own subpackage with
its own `package-info.java`.

Subpackage visibility is load-bearing, not cosmetic. Types that exist only to support one workflow —
factories above all — are **package-private** so that partially constructed state and raw secrets
cannot escape the package that knows how to handle them. Only the type a different package actually
calls is `public`.

## Keep controllers thin

A controller method may do exactly three things: accept a validated request, delegate to one
service, and shape the HTTP response (`ResponseEntity.noContent()`, a response record). It must not
branch on business state, call a repository, catch a domain exception, or return a persistence
entity.

## Extract a shared collaborator instead of duplicating a rule

When the same lookup, check, or construction appears in a second service, move it behind a
collaborator rather than copying it. The repository already did this twice, and both moves are the
precedent to follow:

- `UserFinder` (`refactor(user): centralize account lookup and activation checks`) owns every "load
  this user and decide what an absent or disabled account means" path. Services no longer repeat
  `findById(...).orElseThrow(...)` plus an `isActive()` check. Its methods take a
  `Supplier<? extends RuntimeException>` so each caller keeps its own not-found semantics without
  reimplementing the lookup.
- The factories (`refactor(service): build every entity through a factory`) own construction, for
  the same reason: a duplicated invariant is an invariant that will eventually be forgotten at one
  of its call sites.

A collaborator is preferable to a base class or a static utility: it is injected, it can be
replaced in a test, and its dependencies stay explicit.

## Events for post-commit work

Work that must not roll back the command and must not run inside its transaction — sending an
email, above all — is published as an application event (`EmailVerificationIssued`,
`PasswordResetIssued`) and handled after commit. A service must never call an SMTP client, an HTTP
client, or any other remote system inline inside a `@Transactional` method.
