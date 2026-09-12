package dev.ngb.backend.model;

/**
 * How one calendar sync run ended.
 *
 * <p>{@link #UNCHANGED} is worth distinguishing from {@link #SUCCESS}: a feed whose digest has not
 * moved needed no work, and counting that as a change would make the sync history useless for
 * spotting a feed that has quietly stopped updating.</p>
 */
public enum CalendarSyncOutcome {
    /** Completed and applied changes. */
    SUCCESS,
    /** Completed, but some events could not be applied. */
    PARTIAL,
    /** Could not be completed. */
    FAILED,
    /** Completed with nothing to do; the feed had not changed. */
    UNCHANGED
}
