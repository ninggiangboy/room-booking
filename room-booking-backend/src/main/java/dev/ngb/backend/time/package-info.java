/**
 * Calendar and time-zone primitives shared by every domain that reasons about dates.
 *
 * <p>The platform separates two kinds of time. An event or deadline is an absolute point on the
 * timeline, carried as {@link java.time.Instant} and stored as PostgreSQL {@code timestamptz}. A
 * stay night, calendar restriction, or house rule is a civil value in the listing's Internet
 * Assigned Numbers Authority (IANA) time zone, carried as {@link java.time.LocalDate} or
 * {@link java.time.LocalTime}. Converting between the two always requires an explicit zone and a
 * documented boundary rule; neither the server default zone nor the caller's device may supply it.
 * </p>
 *
 * <p>Reference: {@code docs/features/date-time-and-time-zone-handling.md}.</p>
 */
package dev.ngb.backend.time;
