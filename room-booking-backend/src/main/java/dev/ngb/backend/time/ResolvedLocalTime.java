package dev.ngb.backend.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Immutable provenance of one civil-time-to-instant conversion.
 *
 * <p>This is the value a booking snapshots so a deadline can be explained and reproduced later.
 * Keeping the requested local value next to the resolved one makes a daylight-saving adjustment
 * visible instead of hiding it inside a single timestamp, and keeping the offset next to the zone
 * records what the rules said at decision time.</p>
 *
 * @param requestedLocalDateTime civil value supplied by the domain, before any policy was applied
 * @param resolvedLocalDateTime civil value that actually exists in the zone, equal to the requested
 *     value unless it fell in a daylight-saving gap
 * @param zone IANA zone used to interpret the civil value
 * @param offset offset the zone rules produced for the resolved value
 * @param instant absolute point on the timeline the domain must compare against
 * @param resolution which ambiguity policy, if any, was applied
 */
public record ResolvedLocalTime(
        LocalDateTime requestedLocalDateTime,
        LocalDateTime resolvedLocalDateTime,
        ZoneId zone,
        ZoneOffset offset,
        Instant instant,
        LocalTimeResolution resolution) {

    /**
     * Reports whether the zone rules moved the requested civil value.
     *
     * @return {@code true} when the requested local value does not exist in this zone
     */
    public boolean wasAdjusted() {
        return resolution == LocalTimeResolution.SHIFTED_FROM_GAP;
    }

    /**
     * Reports whether the requested civil value occurred twice in this zone.
     *
     * @return {@code true} when an overlap policy selected between two possible offsets
     */
    public boolean wasAmbiguous() {
        return resolution == LocalTimeResolution.AMBIGUOUS_EARLIER_OFFSET
                || resolution == LocalTimeResolution.AMBIGUOUS_LATER_OFFSET;
    }
}
