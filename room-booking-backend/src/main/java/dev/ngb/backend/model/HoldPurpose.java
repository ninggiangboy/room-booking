package dev.ngb.backend.model;

/**
 * Why inventory is being held temporarily.
 *
 * <p>The purpose decides how long the hold may live and what releasing it means, which is why it is
 * recorded rather than inferred: a guest's checkout hold should expire in minutes, while a hold
 * awaiting a host's decision has to survive long enough for a person to answer.</p>
 */
public enum HoldPurpose {
    /** Held while a guest completes payment. */
    CHECKOUT,
    /** Held while a host decides whether to accept a request. */
    HOST_APPROVAL,
    /** Held while a change to an existing booking is being confirmed. */
    MODIFICATION
}
