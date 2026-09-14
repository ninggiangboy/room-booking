package dev.ngb.backend.analytics.internal.model.experiment;

/**
 * Whether observed assignment counts match the declared allocation.
 *
 * <p>A mismatch means the randomisation did not do what it claimed, which makes every comparison
 * downstream of it suspect.</p>
 */
public enum SampleRatioStatus {

    /** Counts match the declared allocation. */
    PASS,

    /** Diverging enough to watch. */
    WARN,

    /** Diverged materially; interpretation is suspended. */
    FAIL,

    /** The design has no fixed ratio to check against. */
    NOT_APPLICABLE
}
