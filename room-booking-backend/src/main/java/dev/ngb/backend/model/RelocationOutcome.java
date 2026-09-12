package dev.ngb.backend.model;

/**
 * How a relocation case ended.
 *
 * <p>A case that says it rebooked the guest must name the replacement booking, and that booking can
 * never be the one that failed.</p>
 */
public enum RelocationOutcome {
    /** The guest moved into a replacement stay. */
    REBOOKED,
    /** No suitable stay was found and the money was returned. */
    REFUNDED,
    /** The guest refused every offer. */
    GUEST_DECLINED,
    /** The guest found their own stay, possibly with expenses reimbursed. */
    GUEST_SELF_ARRANGED,
    /** The guest could not be reached in time. */
    NO_CONTACT
}
