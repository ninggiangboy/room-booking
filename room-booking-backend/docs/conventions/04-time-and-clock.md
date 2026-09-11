# 4. Time, dates, and time zones

The authoritative design is `docs/features/date-time-and-time-zone-handling.md`. This document is
the short, enforceable form of it. These are correctness rules: a violation does not throw, it
produces an oversold night, a hidden sellable night, or a report attributed to the wrong day.

## Two kinds of time, never interchangeable

- An event or deadline is an **absolute point on the timeline**: `Instant` in Java, `timestamptz`
  in PostgreSQL.
- A stay night, calendar restriction, or house rule is a **civil value in the listing's IANA time
  zone**: `LocalDate` or `LocalTime`, stored as `date` or `time`.

Converting between the two always requires an explicit zone and a documented boundary rule. Neither
the server default zone nor the caller's device may supply it.

## The application clock is the only writer of time

Inject `java.time.Clock` and call `clock.instant()`. The following are forbidden in application
code:

- `Instant.now()`, `LocalDate.now()`, `LocalDateTime.now()`, `System.currentTimeMillis()`, `new Date()`
- `now()`, `CURRENT_DATE`, `CURRENT_TIMESTAMP` in SQL, and `DEFAULT now()` on a column the
  application writes

The shared clock ticks at microsecond resolution, matching what `timestamptz` can store, so an
instant held in memory stays equal to the value read back.

## One decision instant per command

Read the clock **once** at the top of a workflow and pass that instant everywhere it is needed —
into the factory, onto the superseded rows, into the expiry calculation.

`AuthTokenFactory.create(userId, type, issuedAt, ttl)` takes the issuing instant as a parameter for
exactly this reason: a workflow that consumes older tokens and issues a new one stamps every row
with one instant instead of several reads that disagree by microseconds. Factories never read the
clock themselves.

When a row's timestamp is written by auditing rather than by your code, reuse the persisted value
rather than taking a second reading. `AuthenticationService` persists the user first and reuses the
saved user's `createdAt` as the initial role's grant instant; the earlier version stamped the role
from the clock before the save, so auditing's later read always landed after it.

## There is no global "today"

A civil date without a zone identifies nothing. When one query spans listings in many zones:

- Only the **earliest** civil date in force anywhere may reject a past stay date — the latest bound
  would discard nights still sellable in western zones.
- A per-listing date predicate must bind the command's decision instant cast to `timestamptz`.
  `now()` is a second clock, and `AT TIME ZONE` reverses meaning for a plain `timestamp` operand.

## The UTC runtime is load-bearing

The JVM default zone is pinned to UTC before Spring starts and the application refuses to start
otherwise; the database session zone is pinned on connection creation; the test JVM sets
`user.timezone=UTC`. This is not cosmetic. Spring Data JDBC converts `LocalDate`, `LocalTime`, and
`LocalDateTime` through `java.sql.Timestamp` using `ZoneId.systemDefault()`, and the PostgreSQL
driver advertises that same zone as the session zone — so a non-UTC host changes the stored civil
values and the result of every server-side date expression.

Pinning the runtime does **not** license ambient-zone reasoning. It removes a failure mode; it does
not remove the need to name a zone at every conversion.

## Zones are Region/City identifiers

A property zone must be a full IANA `Region/City` identifier, validated by `@IanaZoneId`.
Single-segment aliases such as `UTC`, `GMT`, and `CET` are rejected: they resolve, but they identify
no jurisdiction whose rules can be followed when local law changes.

## Conversions go through StayCalendar

`dev.ngb.backend.time.StayCalendar` owns every conversion between an instant and a listing-local
civil value, including daylight-saving gap and overlap policy and the civil-date bounds a
multi-zone availability query needs. Do not hand-roll a conversion in a service.

Resolve a spring-forward gap from the zone rules' transition instant. `ZonedDateTime.of()` shifts
the local time by the gap's full length instead of landing on the first valid instant — that was a
real bug, fixed in `fix(time): resolve gap correctly and share one decision instant`.

## Deadlines

A contractual instant is snapshotted when the contract is formed, never recalculated later from
current rules. Equality on a deadline means expired: a token is usable only while
`expiresAt.isAfter(now)`.
