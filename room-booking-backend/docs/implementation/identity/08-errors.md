# Errors

Every identity error uses the platform's standard shape (`config.ApiErrorResponse`):

```json
{
  "timestamp": "2026-01-01T00:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "email is invalid",
  "data": { "field": "email" },
  "path": "/api/v1/auth/register"
}
```

`status` drives HTTP handling, `code` is what client code should branch on, `message` is a
human-readable explanation that may change, and `data` carries structured, safe context. Clients
must not parse `message`.

## Stable codes

| Code | HTTP | Exception type | Disclosure class | Used by |
| --- | --- | --- | --- | --- |
| `VALIDATION_ERROR` | 400 | `ValidationException` (platform base) | Safe — names only the offending field | Every endpoint with a request body or password policy |
| `INVALID_EMAIL_VERIFICATION_TOKEN` | 400 | `InvalidEmailVerificationTokenException` | Safe — never distinguishes "unknown" from "expired" from "consumed" | [Confirm verification](03-email-verification.md) |
| `INVALID_PASSWORD_RESET_TOKEN` | 400 | `InvalidPasswordResetTokenException` | Safe — same non-distinguishing shape | [Reset password](04-password-management.md) |
| `INVALID_CREDENTIALS` | 401 | `InvalidCredentialsException` | Enumeration-resistant — identical for unknown email and wrong password | [Login](02-login-and-sessions.md), [Change password](04-password-management.md) |
| `INVALID_REFRESH_TOKEN` | 401 | `InvalidRefreshTokenException` | Safe — never reveals whether the token was reuse, expired, or unknown | [Refresh](02-login-and-sessions.md) |
| `USER_ACCOUNT_DISABLED` | 403 | `UserAccountDisabledException` | Discloses status (`SUSPENDED`/`CLOSED`) only to the authenticated owner of the request | Every endpoint requiring an active account |
| `USER_NOT_FOUND` | 404 | `UserNotFoundException` | Only reachable for an authenticated principal whose own row vanished between token issue and use | [`GET /users/me`](05-account-profile.md), [Host onboarding](06-host-onboarding.md) |
| `EMAIL_ALREADY_REGISTERED` | 409 | `EmailAlreadyRegisteredException` | Discloses that the address is claimed — accepted trade-off for a synchronous sign-up form | [Registration](01-registration.md) |
| `EMAIL_ALREADY_VERIFIED` | 409 | `EmailAlreadyVerifiedException` | Safe — only reachable by the authenticated owner | [Request verification](03-email-verification.md) |
| `EMAIL_VERIFICATION_RATE_LIMITED` | 429 | `EmailVerificationRateLimitException` | Safe; carries `Retry-After` header and `data.retryAfterSeconds`/implicit retry instant | [Request verification](03-email-verification.md) |

## Deliberately non-disclosing endpoints

Two endpoints return the **same** success response regardless of whether the target account exists,
specifically to prevent enumeration:

- [`POST /api/v1/auth/password/forgot`](04-password-management.md#request-a-password-reset) always
  returns `204 No Content` for a syntactically valid email.
- [`GET /api/v1/users/email-exists`](05-account-profile.md#check-email-availability) is the
  documented **exception** — it exists specifically to answer "is this address taken," so its
  disclosure is the endpoint's entire purpose, not a leak.

## What the client should do

- **400 codes** (`VALIDATION_ERROR`, the two `INVALID_*_TOKEN` codes): fix the named field or
  restart the flow that issues a fresh token; do not retry the same request unchanged.
- **401 codes**: re-prompt for credentials, or attempt one more login if a refresh failed —
  `INVALID_REFRESH_TOKEN` following a reuse-detected event means every session on that lineage is
  gone and the user must log in again.
- **403 `USER_ACCOUNT_DISABLED`**: stop retrying; the account cannot proceed until an operator
  action changes its status (no such action exists yet for reactivation — see
  [`09-roadmap.md`](09-roadmap.md)).
- **404 `USER_NOT_FOUND`**: treat as a session invalidation; discard tokens and require login.
- **409 codes**: prompt the user toward login or "resend verification" rather than retrying the same
  write.
- **429**: wait until `Retry-After`/`retryAfterSeconds` before retrying.

## Status

Implemented for every code above; no code in this table is reserved for unimplemented behavior.
