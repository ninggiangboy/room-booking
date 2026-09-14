package dev.ngb.backend.support.internal.model.evidence;

/**
 * The scan state of {@code case_evidence_items}.
 */
public enum CaseScanState {

    /** Not applicable. */
    NOT_APPLICABLE,

    /** Pending. */
    PENDING,

    /** Clean. */
    CLEAN,

    /** Infected. */
    INFECTED,

    /** Unscannable. */
    UNSCANNABLE
}
