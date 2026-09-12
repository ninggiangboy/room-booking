package dev.ngb.backend.model;

/**
 * How urgent an incident is, and what it obliges the platform to do.
 *
 * <p>Stored alongside an orderable rank, paired to it by a check constraint. A model may raise
 * severity; it may never lower a declared safety report below the deterministic floor, and a
 * trigger refuses any lowering without a reason and, on a safety report, a named reviewer.</p>
 */
public enum IncidentSeverity {
    /** Immediate threat or potentially severe harm. */
    S0_SAFETY_CRITICAL,
    /** Cannot enter, uninhabitable, stranded, or severe active disruption. */
    S1_URGENT,
    /** Material stay impact requiring a time-bound remedy. */
    S2_HIGH,
    /** An ordinary service issue. */
    S3_STANDARD,
    /** A question or evidence with no current impact. */
    S4_INFORMATIONAL;

    /**
     * Orderable form of this severity, where zero is the most urgent.
     *
     * <p>Written to {@code incidents.severity_rank}, which a check constraint pairs with the
     * severity itself so the two cannot disagree.</p>
     *
     * @return rank between zero and four
     */
    public short rank() {
        return (short) ordinal();
    }
}
