# Implementation documentation

`docs/data-model/` and `docs/modules/` describe schema and module boundaries. `docs/features/`
describes target designs, implemented and not. This directory describes **what the code that
actually runs does today**, endpoint by endpoint: request shape, response shape, the flow from
controller to database, the rules each step enforces and why, the errors it can produce, and the
events it publishes.

Only `identity` has live application code as of this writing (two controllers, thirteen endpoints —
see [`../modules/README.md`](../modules/README.md) for the full module list). As other modules gain
services and controllers, they get their own subdirectory here, following the same layout and the
same per-use-case template `identity/` establishes.

## Status legend

Every use-case document ends with a `## Status` line using one of these three words:

| Status | Meaning |
| --- | --- |
| **Implemented** | The endpoint exists, is wired to a controller, and the described behavior is exactly what the code does. |
| **Partial** | The endpoint exists but the description includes a documented gap — a rule that is not yet enforced, or a case the code does not yet handle. |
| **Planned** | Nothing runs yet. Content under this status is design, not implementation, and belongs to the roadmap rather than the numbered use-case files. |

## Diagram format: an intentional exception

Every other normative document in `docs/` uses fenced ` ```text ` ASCII diagrams. This directory
uses [Mermaid](https://mermaid.js.org/) instead — `sequenceDiagram`, `stateDiagram-v2`,
`erDiagram`, and `flowchart` — because a request/response/error walkthrough reads better as a
rendered sequence diagram than as hand-drawn boxes, and GitHub renders Mermaid natively in a
Markdown preview. This is recorded as a deliberate, scoped exception in
[`../conventions/06-documentation-and-javadoc.md`](../conventions/06-documentation-and-javadoc.md);
do not carry it into any other document without a similar deliberate decision.

## Layout

```
docs/implementation/
├── README.md                     this file
└── identity/
    ├── 00-overview.md            principal model, erDiagram, module map, configuration
    ├── 01-registration.md        POST /api/v1/auth/register
    ├── 02-login-and-sessions.md  POST /api/v1/auth/{login,refresh,logout}
    ├── 03-email-verification.md  POST /api/v1/auth/email-verification/{request,confirm}
    ├── 04-password-management.md PUT /api/v1/users/me/password, POST /api/v1/auth/password/{forgot,reset}
    ├── 05-account-profile.md     GET /api/v1/users/me, GET .../email-exists, DELETE /api/v1/users/me
    ├── 06-host-onboarding.md     POST /api/v1/users/me/host-capability
    ├── 07-authorization.md       capability catalog, grants, evaluation, the JWT filter
    ├── 08-errors.md              every stable error code, its HTTP status, and its disclosure class
    └── 09-roadmap.md             what is designed in docs/features/ but not built
```

## Content sources

Every claim in these documents traces to one of: the request/response records under
`identity/internal/web/`, the `public static final String CODE` constants under
`identity/internal/exception/`, `config/ApiExceptionHandler`, the `@Value`-injected configuration
keys and their defaults in the services that read them, and the Liquibase changesets that define
the tables involved. Roadmap content traces to
[`../features/identity-accounts-and-access.md`](../features/identity-accounts-and-access.md).
Nothing here is invented past those sources.
