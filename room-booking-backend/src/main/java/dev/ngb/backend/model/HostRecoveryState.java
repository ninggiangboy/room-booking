package dev.ngb.backend.model;

/**
 * How far the collection of a host debt has got.
 *
 * <p>A write-off is a new approved accounting event. It does not delete the receivable, the causal
 * evidence, or the host's history.</p>
 */
public enum HostRecoveryState {
    /** Raised, with nothing collected yet. */
    OPEN,
    /** Being collected from the host's own balance or reserve. */
    OFFSETTING,
    /** Awaiting a payment or repayment from the host. */
    COLLECTION_PENDING,
    /** Fully collected. */
    RECOVERED,
    /** The host is contesting it. */
    DISPUTED,
    /** Approved as unrecoverable. */
    WRITTEN_OFF,
    /** Needs a person to decide what happens next. */
    MANUAL_REVIEW
}
