package dev.ngb.backend.model;

/**
 * Whether an ingested artifact is believed to cover its whole period.
 *
 * <p>A partial import that is treated as complete produces a reconciliation that reports missing
 * internal records which are not missing at all, so the belief is recorded rather than assumed.</p>
 */
public enum ArtifactCompleteness {
    /** No completeness marker was available. */
    UNKNOWN,
    /** Sequence and markers say the whole period is present. */
    COMPLETE,
    /** Known to be missing rows or to cover only part of the period. */
    PARTIAL,
    /** Failed structural validation. */
    CORRUPT
}
