package dev.ngb.backend.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Converts between absolute instants and the civil calendar a listing actually sells.
 *
 * <p>{@code @Component} makes this the one injectable place that performs the conversion, and
 * Lombok generates constructor injection for the shared {@link Clock}. Centralising it is the point:
 * a stay night is a civil date in the listing's zone, so deriving "today" from the server default
 * zone, from {@code CURRENT_DATE}, or from a caller's device silently shifts inventory by a day for
 * every listing in a different zone.</p>
 *
 * <p>Reference: {@code docs/features/date-time-and-time-zone-handling.md}.</p>
 */
@Component
@RequiredArgsConstructor
public class StayCalendar {

    /**
     * Furthest offset behind Coordinated Universal Time (UTC) in the zone database.
     *
     * <p>A listing in this offset holds the smallest civil date in force anywhere, which makes it
     * the safe lower bound when one query spans listings in many zones.</p>
     */
    public static final ZoneOffset MIN_CIVIL_OFFSET = ZoneOffset.ofHours(-12);

    /**
     * Furthest offset ahead of UTC in the zone database, which holds the largest civil date in force.
     */
    public static final ZoneOffset MAX_CIVIL_OFFSET = ZoneOffset.ofHours(14);

    private final Clock clock;

    /**
     * Returns the single decision instant a command should reuse for all of its checks.
     *
     * @return current instant from the shared clock
     */
    public Instant now() {
        return clock.instant();
    }

    /**
     * Returns the current civil date in one zone.
     *
     * @param zone listing or property zone
     * @return today's date as the property experiences it
     */
    public LocalDate today(ZoneId zone) {
        return today(zone, now());
    }

    /**
     * Returns the civil date in one zone at an explicit instant.
     *
     * @param zone listing or property zone
     * @param at decision instant
     * @return civil date in force in that zone at that instant
     */
    public LocalDate today(ZoneId zone, Instant at) {
        return LocalDate.ofInstant(at, zone);
    }

    /**
     * Returns the smallest civil date in force anywhere on Earth at an instant.
     *
     * <p>Because civil dates span roughly twenty-six hours of offsets, no single date is correct for
     * every listing. This bound is index-friendly and never excludes a night that some listing can
     * still sell, so a multi-zone availability query uses it as the coarse filter applied before the
     * exact per-listing date.</p>
     *
     * <p>It is also the only bound a request may reject against: a stay date before this value has
     * already passed in every zone, so no listing anywhere can satisfy it. A date on or after it is
     * still sellable somewhere and must be filtered per listing instead.</p>
     *
     * @param at decision instant
     * @return earliest civil date any zone is currently observing
     */
    public LocalDate earliestCivilDateAnywhere(Instant at) {
        return at.atOffset(MIN_CIVIL_OFFSET).toLocalDate();
    }

    /**
     * Returns the largest civil date in force anywhere on Earth at an instant.
     *
     * <p>This is the complementary bound: a stay date on or after it has not yet passed in any zone,
     * so it is present or future for every listing. It bounds the upper end of a multi-zone query
     * and answers "is this date safe everywhere", and it must not be used to reject a past date,
     * which is what {@link #earliestCivilDateAnywhere(Instant)} decides.</p>
     *
     * @param at decision instant
     * @return latest civil date any zone is currently observing
     */
    public LocalDate latestCivilDateAnywhere(Instant at) {
        return at.atOffset(MAX_CIVIL_OFFSET).toLocalDate();
    }

    /**
     * Reports whether a stay night has already passed for one listing.
     *
     * @param stayDate civil night being examined
     * @param zone listing zone that defines the night
     * @param at decision instant
     * @return {@code true} when the night is before today in that zone
     */
    public boolean isStayDatePast(LocalDate stayDate, ZoneId zone, Instant at) {
        return stayDate.isBefore(today(zone, at));
    }

    /**
     * Counts the nights in a half-open stay range.
     *
     * <p>The count is a difference between civil dates, so a daylight-saving transition inside the
     * stay cannot change it: 7 March to 10 March is always three nights even when the elapsed
     * wall-clock duration is 71 or 73 hours.</p>
     *
     * @param checkIn inclusive arrival date
     * @param checkOut exclusive departure date
     * @return number of nights occupied
     * @throws IllegalArgumentException when the range is empty or inverted
     */
    public static int nights(LocalDate checkIn, LocalDate checkOut) {
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException(
                    "check-out " + checkOut + " must be after check-in " + checkIn);
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(checkIn, checkOut));
    }

    /**
     * Resolves an arrival's civil date and time to an instant.
     *
     * <p>An ambiguous arrival selects the earlier offset so the guest is never told they are late
     * for a window that has not opened yet.</p>
     *
     * @param date civil arrival date in the listing zone
     * @param localTime official local arrival time, such as the listing's check-in opening
     * @param zone listing zone
     * @return conversion provenance including the resulting instant
     */
    public ResolvedLocalTime resolveArrival(LocalDate date, LocalTime localTime, ZoneId zone) {
        return resolve(date, localTime, zone, true);
    }

    /**
     * Resolves a departure's civil date and time to an instant.
     *
     * <p>An ambiguous departure selects the later offset so a guest keeps the full local day they
     * were sold.</p>
     *
     * @param date civil departure date in the listing zone
     * @param localTime official local departure time, such as the listing's check-out deadline
     * @param zone listing zone
     * @return conversion provenance including the resulting instant
     */
    public ResolvedLocalTime resolveDeparture(LocalDate date, LocalTime localTime, ZoneId zone) {
        return resolve(date, localTime, zone, false);
    }

    private static ResolvedLocalTime resolve(
            LocalDate date, LocalTime localTime, ZoneId zone, boolean preferEarlierOffset) {
        LocalDateTime requested = LocalDateTime.of(date, localTime);
        List<ZoneOffset> validOffsets = zone.getRules().getValidOffsets(requested);

        // An empty list means a spring-forward gap; two entries mean a fall-back overlap.
        ZonedDateTime resolved = switch (validOffsets.size()) {
            case 0, 1 -> ZonedDateTime.of(requested, zone);
            default -> ZonedDateTime.ofLocal(
                    requested,
                    zone,
                    preferEarlierOffset ? validOffsets.getFirst() : validOffsets.getLast());
        };

        LocalTimeResolution resolution = switch (validOffsets.size()) {
            case 0 -> LocalTimeResolution.SHIFTED_FROM_GAP;
            case 1 -> LocalTimeResolution.EXACT;
            default -> preferEarlierOffset
                    ? LocalTimeResolution.AMBIGUOUS_EARLIER_OFFSET
                    : LocalTimeResolution.AMBIGUOUS_LATER_OFFSET;
        };

        return new ResolvedLocalTime(
                requested,
                resolved.toLocalDateTime(),
                zone,
                resolved.getOffset(),
                resolved.toInstant(),
                resolution);
    }
}
