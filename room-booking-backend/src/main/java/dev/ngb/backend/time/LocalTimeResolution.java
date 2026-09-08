package dev.ngb.backend.time;

/**
 * Records how a civil local time was mapped onto the timeline.
 *
 * <p>A local date and time is not always one instant. A daylight-saving spring-forward creates a
 * gap in which a wall-clock time never occurs, and a fall-back creates an overlap in which it
 * occurs twice. Storing which case applied, alongside the resolved offset, makes a contractual
 * deadline reproducible instead of something later recalculated from current rules.</p>
 */
public enum LocalTimeResolution {

    /** The local time occurred exactly once; no policy was needed. */
    EXACT,

    /** The local time fell in a spring-forward gap and was shifted forward to the first valid instant. */
    SHIFTED_FROM_GAP,

    /** The local time occurred twice and the earlier offset was selected, as required for arrivals. */
    AMBIGUOUS_EARLIER_OFFSET,

    /** The local time occurred twice and the later offset was selected, as required for departures. */
    AMBIGUOUS_LATER_OFFSET
}
