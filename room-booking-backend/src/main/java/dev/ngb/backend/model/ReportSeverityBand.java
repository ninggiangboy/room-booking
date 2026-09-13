package dev.ngb.backend.model;

/**
 * How urgently a report must be looked at.
 *
 * <p>An urgent safety report must carry a review task. Safety intake stays open to somebody the
 * platform has otherwise restricted, which is why nothing here couples a report to a restriction.</p>
 */
public enum ReportSeverityBand {
    /** Credible immediate danger or severe harm. */
    URGENT_SAFETY,
    /** Serious, needing prompt review. */
    HIGH,
    /** The ordinary case. */
    STANDARD,
    /** Minor or informational. */
    LOW;
}
