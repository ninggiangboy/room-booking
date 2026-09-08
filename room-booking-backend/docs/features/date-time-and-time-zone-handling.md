# Date, time, and time-zone handling

## Purpose

Define one semantics for every date, time, and time-zone value in the Room Booking marketplace, and
make that semantics enforceable rather than advisory.

The central question is not how to format a timestamp. It is this: **a date-time value does not
carry enough context to identify a point in time, so every conversion between a civil calendar value
and an absolute instant must name the zone that authorises it.** When the zone is implicit, the
platform does not fail loudly. It sells a night that already ended, hides a night it could still
sell, reports revenue against the wrong day, or produces different results on a developer machine
and in production. Each of those is a correctness defect that looks like a formatting detail.

This document is the authoritative reference for the time semantics that
[Platform foundation](platform-foundation.md) states as a principle and that
[Availability, reservation, and booking lifecycle](availability-reservation-and-booking.md),
[Booking modification, cancellation, and refund policy](cancellation-modification-and-refund.md),
[Global location search](location-search.md),
[Messaging, notifications, and stay operations](messaging-notifications-and-stay-operations.md), and
[Ledger, reconciliation, and host payout](ledger-reconciliation-and-host-payout.md) each depend on.
Those documents own their own policies; this one owns the calendar primitives they share. The
cross-domain invariants it protects are listed in
[the master map](../marketplace-problem-breakdown.md).

## Status and dependencies

The runtime foundation described in [Runtime invariants](#runtime-invariants) and
[Shared calendar primitives](#shared-calendar-primitives) is **implemented**. Everything that depends
on listings, availability, and bookings is a **target design**, because those aggregates do not have
Java entities yet: the migrations create the tables, but no code writes them.

Implemented today:

| Capability | Where |
| --- | --- |
| Coordinated Universal Time (UTC) Java Virtual Machine (JVM) default zone, pinned before Spring starts | `RoomBookingBackendApplication` |
| Startup guard that refuses a non-UTC default zone | `TimeConfig#requireUtcDefaultZone` |
| Shared UTC `Clock` truncated to database resolution | `TimeConfig#clock` |
| UTC database session zone on every pooled connection | `spring.datasource.hikari.connection-init-sql` |
| UTC test runtime | `build.gradle` test task `user.timezone` |
| Internet Assigned Numbers Authority (IANA) zone validation and a Bean Validation constraint | `dev.ngb.backend.time.IanaTimeZone`, `IanaZoneId` |
| Civil-date and daylight-saving conversion primitives | `dev.ngb.backend.time.StayCalendar`, `ResolvedLocalTime` |
| Application ownership of its own audit timestamps | [`011-application-owned-timestamps.sql`](../../src/main/resources/db/changelog/changes/011-application-owned-timestamps.sql) |

Schema baseline already in place: `listings.timezone`, `listings.check_in_from`,
`listings.check_in_until`, `listings.check_out_until`
([`002-listing-catalog.sql`](../../src/main/resources/db/changelog/changes/002-listing-catalog.sql),
[data model](../data-model/002-listing-catalog.md)); `availability_days.stay_date`
([`003-calendar-pricing.sql`](../../src/main/resources/db/changelog/changes/003-calendar-pricing.sql),
[data model](../data-model/003-calendar-pricing.md)); `bookings.check_in`, `bookings.check_out`,
`bookings.payment_expires_at`
([`004-booking.sql`](../../src/main/resources/db/changelog/changes/004-booking.sql),
[data model](../data-model/004-booking.md)).

Missing capabilities, in the order they should be built:

1. A user-facing zone on the `users` table, required before any scheduled notification.
2. Materialised stay and deadline instants on `bookings`, required before any scheduled booking
   state transition.
3. Listing, availability, and booking aggregates that consume `StayCalendar` instead of deriving
   dates locally.
4. The multi-zone availability query shape defined in
   [Civil dates across many zones](#civil-dates-across-many-zones).
5. A recorded, audited listing time-zone change procedure.

## Goals

- One definition of "now", "today", and "this night" that every domain reuses.
- Conversions between civil values and instants that are explicit, reproducible, and auditable.
- Identical behaviour on a developer machine, in continuous integration, and in production.
- Daylight-saving correctness for night counting, deadlines, and house rules.
- Failures that surface at startup or at insert time rather than as wrong data.

## Non-goals

- User-interface date pickers, relative-time wording, and locale-specific formatting, which belong to
  presentation and to [Vietnam market readiness](multi-market-compliance-and-localization.md).
- Calendar-system alternatives to the proleptic Gregorian calendar.
- Business policy itself. Advance notice, cancellation bands, and payout cutoffs are owned by their
  feature documents; this document only defines how their dates and instants are computed.
- Clock synchronisation of external providers. A provider timestamp is evidence, never authority.

## Core principles and invariants

### Two kinds of time exist and they are never interchangeable

An **instant** is an absolute point on the timeline: it identifies one moment for every observer.
Events, deadlines, audit records, and money movements are instants.

A **civil value** is what a wall clock and a paper calendar in one place show: a stay night, a
check-in time, a calendar restriction, a house rule. It identifies a moment only once a zone is
supplied.

Converting either way requires an explicit IANA zone. Neither the server default zone, the database
session zone, nor the caller's device may supply a missing zone, and an offset alone is never a
substitute for a zone because an offset carries no future rules.

### Inventory is civil, not elapsed

A stay night is a civil date in the listing's zone, not a twenty-four-hour period. A stay of 7 March
to 10 March always occupies 7, 8, and 9 March — three nights — even when a daylight-saving transition
makes the elapsed wall-clock duration 71 or 73 hours. Nights are therefore counted by subtracting
civil dates, never by dividing a duration.

### There is no global "today"

Civil dates in force at one instant span offsets from UTC−12 to UTC+14, so two distinct calendar
dates are always current somewhere. Any single "today" — from the server, from the database session,
or from the guest's device — is therefore wrong for some listing. "Today" is only meaningful as a
function of a zone.

### One decision instant per command

A command reads the clock once and reuses that instant for every eligibility check and every
deadline it computes. Reading the clock repeatedly inside one decision allows a command to observe
two different "nows" and reach a self-inconsistent conclusion.

Audit timestamps are observations rather than decisions. Spring Data JDBC auditing draws its own
value from the same clock, so two rows written by one command may differ by microseconds. That is
acceptable because no decision depends on it; a value a decision does depend on must be passed
explicitly rather than re-read.

### The UTC runtime is load-bearing

The JVM default zone is pinned to UTC, and startup fails when it is not. This is not tidiness. Two
mechanisms silently inherit the process default zone:

- Spring Data JDBC converts `LocalDate`, `LocalTime`, and `LocalDateTime` to `java.sql.Timestamp`
  through `ZoneId.systemDefault()`.
- The PostgreSQL driver advertises the JVM default zone as the database session time zone, which is
  what `CURRENT_DATE`, `now()::date`, `date_trunc`, and every `timestamptz` cast are evaluated
  against.

Under UTC both become deterministic and gap-free. Under any other zone they become a property of the
host the process happens to run on. The repository has no automated test, so the startup guard is the
only thing enforcing this: it must not be weakened without replacing it with a test that asserts how
Spring Data JDBC maps a civil value.

### A contractual instant is snapshotted, never recalculated

When a policy needs an instant, the resolved local value, the zone, the offset the rules produced,
the resulting instant, and which ambiguity policy applied are all recorded. A confirmed deadline is
never recomputed from current listing configuration or from updated zone data, because the contract
was formed under the old facts.

### The application clock is the only writer of the timestamps it owns

For rows the application writes, `created_at` and `updated_at` come from the shared `Clock` through
Spring Data JDBC auditing. The database has no `DEFAULT now()` on those columns, so an insert path
that forgets a timestamp fails instead of quietly recording the database host's transaction-start
time from a second, unsynchronised clock.

### Equality on a deadline means expired

A deadline is compared with a strict inequality: at exactly the deadline instant, the command is no
longer eligible. This holds for token expiry, payment expiry, and cancellation bands, so no domain
has to define its own boundary.

## Domain vocabulary

| Term | Meaning |
| --- | --- |
| Instant | Absolute point on the timeline; `Instant` in Java, `timestamptz` in PostgreSQL |
| Civil date | Calendar date as experienced in one zone; `LocalDate`, `date` |
| Civil time | Wall-clock time of day in one zone; `LocalTime`, `time` |
| Stay night | One civil date in the listing zone, the unit of inventory |
| Stay range | Half-open `[check_in, check_out)` range of civil dates |
| Listing zone | IANA zone of the property; authority for inventory and contract |
| User zone | IANA zone of a person; authority for presentation and delivery timing only |
| Decision instant | The single instant a command reads from the clock and reuses |
| Deadline | Instant after which a command is no longer eligible; equality is expired |
| Gap | Local time that does not exist because the zone sprang forward |
| Overlap | Local time that occurs twice because the zone fell back |
| Resolution provenance | Requested local value, resolved local value, zone, offset, instant, and policy |
| Civil-date bounds | Earliest and latest civil dates in force anywhere at one instant |

## Why this is a correctness problem

Each failure below is concrete, reproducible, and silent. The examples use
`2026-03-09T23:00:00Z`, a guest in Hanoi searching at 06:00 on 10 March.

### Failure 1 — a global "today" is wrong for some listing

| Listing | Local time | Listing's today | Night of 9 March |
| --- | --- | --- | --- |
| Tokyo, UTC+9 | 10 Mar 08:00 | 10 March | already over, must not be sold |
| Los Angeles, UTC−7 | 9 Mar 16:00 | 9 March | still sellable, check-in just opened |

A filter of `stay_date >= CURRENT_DATE` produces, with a UTC session, `2026-03-09`: the Tokyo listing
still offers a night that ended eight hours ago, which becomes an oversell, a cancellation, a refund,
and a host complaint. With an `Asia/Ho_Chi_Minh` session it produces `2026-03-10`: the Los Angeles
listing loses every remaining same-day booking. Both variants run without error, and the two
environments disagree.

### Failure 2 — persistence inherits the host zone

Spring Data JDBC resolves every `java.time` value to `java.sql.Timestamp` and then converts:

| Value | Conversion applied | Depends on host zone |
| --- | --- | --- |
| `Instant` | `Timestamp.from(instant)` | no |
| `LocalDate` | `date.atStartOfDay(ZoneId.systemDefault())` | yes |
| `LocalTime` | `time.atDate(LocalDate.now()).atZone(ZoneId.systemDefault())` | yes |
| `LocalDateTime` | `ldt.atZone(ZoneId.systemDefault())` | yes |
| `Timestamp` back to a civil value | `Timestamp#toLocalDateTime` | yes |

The conversions cancel out while one process keeps one zone, which is why the defect does not appear
in casual testing. It stops cancelling when a second process uses a different zone, when the database
session zone is set independently — for example by `ALTER ROLE ... SET TimeZone` or a pooler — or, for
`LocalTime`, when the house-rule time falls in a daylight-saving gap on the host's current date, in
which case the stored time is shifted by an hour and never recovers. `listings.check_in_from`,
`check_in_until`, and `check_out_until` are exactly those values.

### Failure 3 — two clocks

A database `DEFAULT now()` and an application `Clock` are different clocks on different hosts, and
`now()` is transaction-start time rather than statement time. Any policy that reads a stored
timestamp back and compares it with the application clock — the email-verification cooldown and
rolling quota do exactly this — then mixes two time sources whose skew is unbounded.

### Failure 4 — silent precision loss

`timestamptz` stores microseconds; `Instant` holds nanoseconds. Writing a nanosecond-precision
instant truncates it, so the value held in memory is no longer equal to the value read back. The
shared clock therefore ticks at microsecond resolution, which removes the class of defect instead of
asking each comparison to tolerate it.

### Failure 5 — a client civil value reinterpreted as an instant

A guest choosing 10–13 March means those dates at the property. If the API accepts an instant or an
offset date-time for a stay date, the server must pick a zone to reduce it back to a date, and any
choice shifts the stay by a day for some callers. Stay dates are accepted only as plain civil dates.

## Canonical representations

| Domain fact | Java | PostgreSQL | JSON | Authority |
| --- | --- | --- | --- | --- |
| Event, deadline, audit, money movement | `Instant` | `timestamptz` | RFC 3339 with `Z` | shared `Clock` |
| Stay night, calendar restriction | `LocalDate` | `date` | `YYYY-MM-DD` | listing zone |
| House rule, check-in or check-out time | `LocalTime` | `time` | `HH:MM` | listing zone |
| Property or listing zone | `ZoneId` | `varchar` validated as IANA | canonical zone id | listing catalog |
| Person's zone | `ZoneId` | `varchar` validated as IANA | canonical zone id | user profile |
| Conversion provenance | `ResolvedLocalTime` | separate local, zone, offset, and instant columns | expanded object | the deciding command |
| Configured lifetime | `Duration` | interval or integer seconds | ISO 8601 duration | configuration |

Rules that follow from the table:

- A `timestamp without time zone` column is never used.
- `ZonedDateTime` and `OffsetDateTime` are not persisted; Spring Data JDBC would store the first as
  text and reduce the second to a local value.
- An instant is never rendered in a fixed offset in an API payload.
- A civil value is never rendered with `Z` appended.

## Runtime invariants

Four settings together make the runtime deterministic, and each has a distinct job:

| Setting | Purpose |
| --- | --- |
| `TimeZone.setDefault("UTC")` in `main` | pins the zone before any Spring, driver, or converter code observes it |
| `TimeConfig#requireUtcDefaultZone` | fails startup, including every Spring test context, if the invariant is broken |
| `spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'` | pins the database session independently of driver negotiation, role defaults, and poolers |
| `user.timezone=UTC` on the test JVM | tests bootstrap Spring directly rather than through `main` |

Container images and process supervisors must also set `TZ=UTC`. If a deployment cannot, the startup
guard stops the process rather than letting it write zone-dependent data.

## Shared calendar primitives

`StayCalendar` is the single injectable place that crosses between instants and civil values.

| Operation | Meaning |
| --- | --- |
| `now()` | the decision instant for this command |
| `today(zone)`, `today(zone, at)` | civil date in force for one listing |
| `earliestCivilDateAnywhere(at)` | smallest civil date any zone is observing; coarse query bound and the only bound a past date may be rejected against |
| `latestCivilDateAnywhere(at)` | largest civil date any zone is observing; a date on or after it is present or future everywhere |
| `isStayDatePast(stayDate, zone, at)` | whether a night has ended for one listing |
| `nights(checkIn, checkOut)` | civil-date difference over a half-open range |
| `resolveArrival(date, time, zone)` | instant for an arrival, preferring the earlier offset |
| `resolveDeparture(date, time, zone)` | instant for a departure, preferring the later offset |

`IanaTimeZone` validates zone text. `isValid` accepts an identifier known to the zone database, which
rejects offsets such as `+07:00` and abbreviations such as `ICT`. `isValidCivilZone` additionally
rejects the offset-only groups `Etc/` and `SystemV/`, because a property stored as `Etc/GMT+7` would
silently stop tracking local time if its region ever adopted a daylight-saving rule. `@IanaZoneId`
applies the same rule at the API boundary.

Prohibited in application code, because each one takes a zone implicitly:

- `LocalDate.now()`, `LocalTime.now()`, `LocalDateTime.now()`, including their `Clock` overloads.
- `Instant.now()`; inject the shared `Clock`.
- `CURRENT_DATE`, `CURRENT_TIMESTAMP`, and `now()` in application SQL.
- `new Date(...)`, `Calendar`, `SimpleDateFormat`, and `java.sql` time types in domain code.
- Deriving a stay date from a timestamp, or a timestamp from a stay date, without naming a zone.

## Civil dates across many zones

Booking resolves one listing, so its zone is known before any date logic runs. Search does not: one
query ranks listings in many zones, and each has its own "today". This is the hardest case and the
one most likely to be implemented incorrectly.

### Per-listing today in SQL

PostgreSQL can apply each listing's own zone, because `AT TIME ZONE` accepts a text expression:

```sql
SELECT ad.listing_id, ad.stay_date
FROM availability_days ad
JOIN listings l ON l.id = ad.listing_id
WHERE ad.stay_date >= :earliestStayDate                    -- coarse, index-friendly bound
  AND ad.stay_date >=                                      -- exact, per listing
      (CAST(:decisionInstant AS timestamptz) AT TIME ZONE l.timezone)::date
  AND ad.availability_status = 'AVAILABLE';
```

Both predicates are derived from the command's single decision instant. `:earliestStayDate` is
`StayCalendar#earliestCivilDateAnywhere(at)` and `:decisionInstant` is the same `at`, so the coarse
bound and the exact per-listing date can never describe two different "nows". Using `now()` for the
second predicate would reintroduce exactly the two-clock defect this document forbids: it is the
database host's transaction-start time, and if it falls on the other side of a listing-local midnight
from the application's instant, the query includes a night that has already ended or excludes one
that is still sellable.

The `CAST(... AS timestamptz)` is mandatory, not stylistic. `AT TIME ZONE` is direction-sensitive and
silently reverses meaning based on the operand type: applied to a `timestamptz` it converts an
instant into that zone's civil time, which is what is wanted here, but applied to a plain `timestamp`
it does the opposite and interprets a civil value as being in that zone. A driver that binds the
parameter without a zone would therefore produce a wrong date rather than an error.

The per-listing predicate is correct for every listing and independent of the session zone, but it is
`STABLE` rather than `IMMUTABLE`, so it cannot appear in an index expression and is evaluated per
row. The coarse bound exists to make the row set small enough for that to be cheap: it uses
`idx_availability_days_search` and can never exclude a night that some listing may still sell.

Bound-then-refine is the required shape. Three shortcuts are prohibited: `CURRENT_DATE` and `now()`,
which depend on the session zone or the database clock, and a single application-computed `today`,
which is wrong for every listing outside the zone it was computed in.

### Where a past date is rejected and where it is filtered

| Situation | Behaviour |
| --- | --- |
| Requested date is before `earliestCivilDateAnywhere(now)` | reject the request; the night has passed in every zone, so no listing anywhere can satisfy it |
| Requested date is before a specific listing's `today(zone)` | exclude that listing from results; not a request error |
| Requested date is on or after `latestCivilDateAnywhere(now)` | valid for every zone; no date-based filtering applies |

The two bounds are not interchangeable, and using the later one to reject is the easy mistake. At
`2026-03-09T23:00:00Z` the earliest civil date anywhere is 9 March and the latest is 10 March.
Rejecting everything before the latest bound would reject 9 March, which the Los Angeles listing in
the table above can still sell for another eight hours.

A consequence is that `@FutureOrPresent` must not be used on a stay date. Bean Validation has no zone
and would apply the server's, which is UTC — correct for no listing in particular. Boundary
validation is limited to shape and ordering: a parseable date, `checkOut > checkIn`, and a range no
longer than the configured maximum. Everything zone-dependent is a domain decision made after the
listing is resolved.

### A civil date is meaningless without its zone

`availability_days` and `booking_nights` deliberately store no zone; they inherit the listing's.
Every query that reads a stay date must therefore join `listings` to obtain `timezone`, and every
projection, cache entry, export, or analytics row that carries a stay date must carry the zone or a
listing reference that resolves it.

## Daylight-saving behavior

### Night counting is unaffected

Nights are a civil-date difference, so a transition inside the stay cannot change the count or the
per-night price rows.

### Gap and overlap resolution

A local time may not exist, or may exist twice. The platform resolves both deterministically and
records which rule applied:

| Case | Rule | Recorded resolution |
| --- | --- | --- |
| Time exists once | use it | `EXACT` |
| Gap, spring forward | shift forward to the first valid instant | `SHIFTED_FROM_GAP` |
| Overlap, arrival | earlier offset, so a guest is never late for a window that has not opened | `AMBIGUOUS_EARLIER_OFFSET` |
| Overlap, departure | later offset, so a guest keeps the full local day they were sold | `AMBIGUOUS_LATER_OFFSET` |

Where a host can be prevented from configuring an impossible recurring local time, prevent it;
otherwise apply the table and store the provenance. `ResolvedLocalTime` carries the requested local
value alongside the resolved one so an adjustment is visible in support and audit rather than hidden
inside a single timestamp.

### Zone-database updates

Zone rules change by political decision, and a runtime or container upgrade can therefore move a
future local time. Two rules follow. A confirmed contractual instant is never recalculated because
zone data changed. A pending future instant that has not yet been contracted may be recomputed, but
only by an explicit, audited job that reports what moved.

### Changing a listing's zone

A zone change alters what every future night and every uncontracted deadline means. It requires host
confirmation, an audit record, validation against future bookings, calendar regeneration, and
operational review when contractual instants would move. Confirmed bookings keep the zone they
snapshotted; see [the booking lifecycle design](availability-reservation-and-booking.md).

## Deadlines and scheduled transitions

Anything a background worker must act on at a moment in time has to be an indexed instant, not a
civil date interpreted per row at scan time. A worker scanning `check_out <= CURRENT_DATE` is wrong
for most zones and cannot use an index on a per-row zone conversion.

The pattern is: resolve the civil value once, at the command that creates or confirms the record,
using the snapshotted zone; store the instant; index it; let workers select on it.
`bookings.payment_expires_at` with `idx_bookings_expiring_payment` already follows this pattern, and
the remaining lifecycle instants should match it.

| Transition | Anchor | Instant to materialise |
| --- | --- | --- |
| Payment expiry | creation instant plus configured hold | `payment_expires_at` (exists) |
| Stay begins, no-show evaluation | `check_in` at `check_in_from` in listing zone | `stay_starts_at` (proposed) |
| Stay completes, review window opens | `check_out` at `check_out_until` in listing zone | `stay_ends_at` (proposed) |
| Free-cancellation band ends | official arrival instant minus policy window | `free_cancellation_until` (proposed) |
| Payout release | policy cutoff in the market's policy zone | owned by [payout](ledger-reconciliation-and-host-payout.md) |

## Presentation, notification, and the user zone

Two zones exist in the same flow and must not be substituted for one another.

| Purpose | Zone | Examples |
| --- | --- | --- |
| Inventory, contract, deadline | listing zone | nights, check-in windows, cancellation bands |
| Presentation and delivery timing | user zone | "your stay starts tomorrow", quiet hours, digests |

The `users` table has no zone column today, so notification scheduling has no correct input, and the
listing zone must not be borrowed as a stand-in — a Hanoi guest booking in Tokyo would receive quiet
hours computed for Tokyo. Until the column exists, a notification may only be sent on an
event-driven trigger, never on a locally scheduled one. Where a user zone is genuinely unknown, fall
back to the market's default zone and record that the fallback was used; never fall back to the
server zone.

An API response should carry a civil value, the zone that gives it meaning, and — where the client
needs an absolute reference — the resolved instant, rather than making the client re-derive any of the
three.

## Conceptual data model

### Current foundation

`listings.timezone` is `varchar(64) NOT NULL` and, per the
[listing catalog data model](../data-model/002-listing-catalog.md), must be validated as IANA in the
application; `IanaTimeZone.parseCivilZone` and `@IanaZoneId` are that validation.
`listings.check_in_from`, `check_in_until`, and `check_out_until` are `time`.
`availability_days.stay_date` and `bookings.check_in`/`check_out` are `date`. Every event column is
`timestamptz`. Application-owned `created_at` and `updated_at` no longer carry a database default.

### Proposed additions

| Table | Column | Type | Why |
| --- | --- | --- | --- |
| `users` | `timezone` | `varchar(64)` nullable | scheduled notification and display; null means "use market default" |
| `bookings` | `stay_starts_at` | `timestamptz` | indexed anchor for arrival, no-show, and reminder work |
| `bookings` | `stay_ends_at` | `timestamptz` | indexed anchor for completion and review-window work |
| `bookings` | `free_cancellation_until` | `timestamptz` nullable | contractual band boundary, resolved once |
| `bookings` | `stay_timezone` | `varchar(64)` | zone snapshot, promoted out of `listing_snapshot` so it is queryable |
| `bookings` | `stay_starts_offset`, `stay_ends_offset` | `varchar(9)` | offsets the rules produced, for reproducibility |
| `bookings` | `stay_time_resolution` | `varchar(32)` | which ambiguity policy applied |

`listings` should also gain a check constraint requiring `timezone` to be non-blank; full IANA
membership cannot be expressed in SQL, so the application remains the authority.

### Constraints and indexes

- `CREATE INDEX ... ON bookings (stay_ends_at) WHERE status = 'CONFIRMED'` for completion work.
- `CREATE INDEX ... ON bookings (stay_starts_at) WHERE status = 'CONFIRMED'` for arrival work.
- `CREATE INDEX ... ON bookings (free_cancellation_until) WHERE free_cancellation_until IS NOT NULL`.
- A check constraint requiring `stay_ends_at > stay_starts_at`.
- `idx_availability_days_search` already supports the coarse bound of the bound-then-refine shape; no
  index can support the per-row zone predicate, which is why the coarse bound is mandatory.

### Migration and backfill

The proposed columns are additive. `stay_starts_at`, `stay_ends_at`, and the offset and resolution
columns are derivable from an existing booking's dates plus the zone in `listing_snapshot`, so they
can be backfilled by an explicit job that resolves each row through `StayCalendar` and records
resolutions that were adjusted or ambiguous for review. `free_cancellation_until` must be backfilled
from the snapshotted policy, never from current listing configuration. Add columns nullable, backfill,
then tighten to `NOT NULL` in a later changeset. Never edit an applied changeset.

## Service boundaries

| Fact | Owner | Never sourced from |
| --- | --- | --- |
| Current instant | shared `Clock` via `StayCalendar#now` | `Instant.now()`, client clock, provider timestamp |
| Civil date for a listing | `StayCalendar#today(zone)` | `CURRENT_DATE`, server default zone, caller device |
| Property zone | listing catalog, validated by `IanaTimeZone` | geocoder guess at read time, country-to-zone mapping |
| Night count | `StayCalendar#nights` | duration division, elapsed hours |
| Arrival and departure instants | `StayCalendar#resolveArrival`/`resolveDeparture` | ad-hoc `atStartOfDay` or `atZone` calls |
| Contractual deadline | the command that formed the contract, snapshotted | recomputation from current configuration |
| Audit timestamps | Spring Data JDBC auditing over the shared clock | database `now()` |

No other component may implement these conversions. A domain service receives a zone and a decision
instant as inputs; it does not discover them.

## API behavior

### Request and response conventions

- Stay dates are `YYYY-MM-DD`. A value carrying a time, an offset, or `Z` is rejected rather than
  coerced.
- Stay ranges are half-open: `checkIn` is inclusive, `checkOut` exclusive, and `checkOut > checkIn`.
- Instants are RFC 3339 with `Z` and microsecond precision.
- A response that includes a civil value also names the zone that gives it meaning.
- A client-supplied instant is treated as intent, never as the decision instant, and can never
  backdate a deadline evaluation.

### Error semantics

| Code | Status | When |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | unparseable date, instant supplied for a stay date, inverted range |
| `STAY_DATE_IN_PAST` | 400 | date earlier than `earliestCivilDateAnywhere(now)`, so already past in every zone |
| `INVALID_TIME_ZONE` | 400 | zone text is not a civil IANA identifier |
| `ADVANCE_NOTICE_NOT_MET` | 409 | arrival instant minus notice has passed for this listing |
| `QUOTE_EXPIRED` | 409 | quote deadline reached; equality counts as expired |

A per-listing past date is not an error: those listings are absent from results. Error messages state
the rule and the zone applied, and never reveal another guest's booking or a private host block.

## Concurrency and idempotency

- A retried command reuses the original decision instant when replaying a stored outcome, so a replay
  cannot produce a different deadline than the response the caller already holds.
- Deadline comparisons happen inside the transaction that claims inventory, using the same decision
  instant as the eligibility checks, so a slow request cannot pass a check that a later instant would
  have failed.
- The database remains the final defence: the `bookings` exclusion constraint over
  `daterange(check_in, check_out, '[)')` is expressed in civil dates and is unaffected by any zone
  question, which is precisely why inventory is modelled civilly.
- A clock that appears to move backwards, for example after a host clock correction, must not turn a
  consumed token or an expired hold back into a usable one; consumption is recorded as state, not
  inferred from time alone.

## Security, privacy, and access control

- A user's zone is personal data that reveals approximate location. It is returned to that user and to
  systems that need delivery timing, not to other guests or hosts.
- A precise property zone narrows a property's location; it is disclosed at the granularity the
  listing already discloses publicly.
- Timestamps in logs and error responses are UTC instants from the shared clock, so an error body
  cannot be used to infer the server's location.
- Only an audited administrative action may change a listing zone or replay a deadline backfill, and
  both record actor, before-and-after values, and reason.

## Observability and operations

- Startup logs the resolved default zone, the database session zone, and the zone-database version, so
  an environment mismatch is visible before traffic arrives.
- A correctness metric counts conversions resolved as `SHIFTED_FROM_GAP` or ambiguous. A non-zero rate
  is expected and small; a spike means a host configured a time inside a transition.
- A metric counts availability queries whose coarse bound and exact per-listing predicate disagree.
  That difference is the population of listings sitting across a date boundary, and it should track
  the number of distinct zones served.
- Alert when the process default zone is not UTC — startup should already have failed — and when the
  zone-database version differs across running instances, which means two instances can resolve the
  same future local time differently.
- Reconcile scheduled work by asserting that no confirmed booking has a `stay_ends_at` in the past
  while still in a pre-completion status.

## Failure behavior

| Condition | Effect if unhandled | Required behaviour |
| --- | --- | --- |
| Host or container default zone is not UTC | civil values stored against the wrong instant | startup fails with an actionable message |
| Database role or pooler forces a session zone | server-side date expressions drift | connection init pins UTC; startup logs both zones |
| Listing zone text is invalid or offset-only | conversions throw or silently lose future rules | rejected at the API boundary and on import |
| Host configures a check-in time inside a gap | stored time shifts by an hour | resolved by policy, provenance recorded, metric incremented |
| Zone database updated between instances | two instances resolve one future local time differently | contracted instants are never recalculated; alert on version skew |
| Stay date arrives as an instant | stay shifts by a day for some callers | rejected as a validation error |
| Insert path omits an audit timestamp | database clock silently substituted | insert fails; no column default exists |
| Instant compared against a database-read value | equality fails on truncation | clock ticks at microsecond resolution |
| Listing zone changed after bookings exist | contractual deadlines move | audited procedure, future-booking validation, snapshot retained |

## Testing and verification

The repository has no automated tests, so every property below is currently unverified and the
runtime guard plus review are the only defences. When testing is established, these are the cases
this domain needs, ordered by how silently they fail without one.

Deterministic, no database required:

- One instant yields a different civil date per zone; a night is past for one listing and still
  sellable for another.
- The civil-date bounds contain every zone in the zone database, a date before the earliest bound is
  past everywhere, and a date on or after the latest bound is past nowhere.
- Night counts are unchanged by a spring-forward and by a fall-back inside the stay.
- A gap time shifts forward; an ambiguous arrival and departure resolve to different instants in the
  documented directions.
- Offsets, abbreviations, wrong case, unknown names, and every non-`Region/City` alias are rejected
  as a property zone, and no zone the database offers passes as civil without a separator.
- The clock reads UTC and ticks at database resolution; the startup guard accepts UTC aliases and
  rejects a real zone.
- How Spring Data JDBC maps a civil value: an instant round-trips, a stay date anchors at UTC
  midnight, every date of a year round-trips, house-rule times including `02:30` round-trip, and a
  non-UTC default zone shifts a stored stay date. This is the case that documents why the UTC
  runtime is load-bearing, so it should be the first one written.
- Equality on a deadline counts as expired, and a consumed token is never usable.

Required as the dependent aggregates are built:

- Real-database tests that write and read `date`, `time`, and `timestamptz` columns through Spring
  Data JDBC and assert exact values, since only these exercise the driver and session zone.
- A repository test for the bound-then-refine availability query with listings in at least three
  zones spanning a date boundary, asserting that each listing's own boundary applies.
- Property tests over random instants and zones asserting the civil-date bounds always contain the
  per-zone date, and that a stay's night count equals the civil-date difference.
- Boundary cases from the [booking lifecycle](availability-reservation-and-booking.md) matrix:
  one-night stay, adjacent stays, leap day, year boundary, gap and overlap stays, closed-to-arrival
  and closed-to-departure.
- A migration test asserting no application-owned timestamp column has a database default and no
  column is `timestamp without time zone`.
- A test that a fixed clock makes an end-to-end command fully deterministic, including the audit
  timestamps it writes.

## Caching, performance, and scaling

- A cached search result whose availability depends on a civil date must include that date, per
  listing, in the cache key, or carry a lifetime shorter than the distance to the nearest listing-local
  midnight. Otherwise a cached "available tonight" outlives the night.
- A per-row zone conversion cannot be indexed, so the coarse civil-date bound is what keeps
  availability queries on an index. Removing it degrades a bounded index scan into a table scan.
- Materialising lifecycle instants converts periodic full-table date scans into bounded index range
  scans, which is the only shape that stays affordable as bookings accumulate.
- Zone-rule lookups are in-memory and cheap; resolving them once per command and passing the result
  down is still preferable to resolving repeatedly inside a loop over nights.

## Appropriate use of AI

No model may decide a date, a zone, a night count, a deadline, or whether a night is bookable. These
are deterministic calculations with legal and financial consequences, and they must be reproducible
from stored provenance.

Two bounded uses are legitimate. Parsing a free-text location or arrival description into a candidate
zone or date is acceptable as a suggestion that a deterministic validator confirms and a human
accepts. Detecting anomalies — a host whose check-in time repeatedly lands in a transition, or a
listing whose zone disagrees with its coordinates — is acceptable as a review signal that opens a
queue item and never mutates data.

## Implementation checklist for any feature touching dates

1. Take one decision instant from `StayCalendar#now` and pass it down; do not read the clock again.
2. Decide, for each value, whether it is an instant or a civil value, and use the type from
   [Canonical representations](#canonical-representations).
3. Resolve the listing zone explicitly and pass it as a parameter; never let a helper discover it.
4. Convert only through `StayCalendar`, and record the returned provenance wherever the result becomes
   contractual.
5. Accept civil dates as civil dates at the API boundary; reject instants for stay dates.
6. Validate shape and ordering at the boundary; make every zone-dependent judgement in the domain.
7. For a multi-listing query, apply the coarse civil-date bound plus the exact per-listing predicate.
8. Materialise and index an instant for anything a worker must act on later.
9. Keep the user zone out of inventory decisions and the listing zone out of delivery timing.
10. Write a fixed-clock test for the boundary, and a gap or overlap test if the value can be a local
    time.

## Target-release dependencies and completion gates

### Dependency 0 — Decisions

Resolve the open decisions below, particularly whether the check-in window may cross midnight, and
which zone governs a market's payout cutoff.

### Dependency 1 — Runtime and primitives

Complete. The UTC runtime, startup guard, shared clock, zone validation, calendar primitives, and
their tests are implemented, and application-owned timestamps have no database default.

Gate: `./gradlew build javadoc` passes, and startup fails on a non-UTC default zone.

### Dependency 2 — Zone-aware identity and notification input

Add `users.timezone` with validation and expose it in the profile API.

Gate: no scheduled notification path reads a listing zone for delivery timing.

### Dependency 3 — Aggregates that consume the primitives

Implement listing, availability, and booking aggregates that take a zone and a decision instant as
inputs, validate `listings.timezone` on write, and hold no local date derivation of their own.

Gate: a static check finds no prohibited call from
[Shared calendar primitives](#shared-calendar-primitives) in `src/main/java`.

### Dependency 4 — Materialised lifecycle instants

Add the proposed `bookings` columns, their indexes, and the backfill job.

Gate: every scheduled transition selects on an indexed instant, and no worker uses `CURRENT_DATE`.

### Dependency 5 — Multi-zone search

Implement the bound-then-refine query and the cache-key rule.

Gate: a repository test with listings in at least three zones across a date boundary passes, and no
availability query references `CURRENT_DATE`.

### Required target behaviour

Zone-correct inventory, snapshotted contractual instants, deterministic gap and overlap resolution,
and identical behaviour across environments are required. They are not optional hardening.

### Designed extension boundaries

Per-market policy zones, pooled hotel inventory, and channel-partner date normalisation all consume
these primitives without changing them.

### Measured-scale capabilities

A denormalised per-listing civil date maintained by a scheduled job, which would make the exact
predicate indexable, is justified only once availability-query latency is measured as a problem. It
adds a staleness window of its own and must not be adopted pre-emptively.

## Verification checklist

### Functional and contract correctness

- One instant produces the correct, differing civil date for listings in different zones.
- A night that has ended in the listing zone is not offered, and a night still open is not hidden.
- Night counts are unchanged by a transition inside the stay.
- A gap time is shifted forward and recorded; an ambiguous arrival and departure resolve to different
  instants in the documented directions.
- A stay date supplied as an instant is rejected; a past date is rejected only when impossible for
  every zone.
- Round-tripping a stay date, a house-rule time, and an instant through PostgreSQL returns the exact
  original value.

### Recovery and operations

- Startup fails on a non-UTC default zone and logs the database session zone.
- An insert that omits an application-owned timestamp fails rather than using a database default.
- A backfill of lifecycle instants is reproducible and reports adjusted and ambiguous resolutions.
- Zone-database version skew across instances raises an alert.

### Security and privacy

- A user's zone is not exposed to other users.
- A listing zone change is audited with actor, before, after, and reason.

## Decisions required before implementation

1. **May a check-in window cross midnight?** `ck_listings_check_in_window` currently requires
   `check_in_until >= check_in_from`, which forbids a 22:00–02:00 window that guest houses and
   late-flight arrivals commonly use. Recommendation: allow it and treat
   `check_in_until < check_in_from` as the following civil day. This changes an applied constraint and
   needs a forward migration plus an architecture decision record.
2. **Which zone governs a market payout cutoff?** Listing zone, market policy zone, or provider zone.
   Recommendation: an explicit market policy zone, versioned with the policy, owned by
   [payout](ledger-reconciliation-and-host-payout.md).
3. **Is `users.timezone` host-provided, client-detected, or derived from the market?**
   Recommendation: client-detected as a default, user-editable, market default when absent, and always
   recorded with its source.
4. **How long may a listing zone change remain pending when future bookings exist?** Recommendation:
   block the change while contractual instants would move and require an explicit operational
   override.
5. **Is a denormalised per-listing civil date ever acceptable?** Recommendation: no, until query
   latency is measured, and then only with an explicit staleness budget.
