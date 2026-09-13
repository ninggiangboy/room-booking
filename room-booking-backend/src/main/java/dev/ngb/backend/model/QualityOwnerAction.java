package dev.ngb.backend.model;

/**
 * How the owner responded to a check result.
 *
 * <p>Waiving requires a named owner and a reason, because it is a decision somebody can be asked
 * about later.</p>
 */
public enum QualityOwnerAction {

    /** No response recorded yet. */
    NONE,

    /** Seen and accepted as known. */
    ACKNOWLEDGED,

    /** The affected records were held back. */
    QUARANTINED,

    /** The dataset was recomputed and republished. */
    RESTATED,

    /** Accepted despite the failure, with a named owner and a reason. */
    WAIVED
}
