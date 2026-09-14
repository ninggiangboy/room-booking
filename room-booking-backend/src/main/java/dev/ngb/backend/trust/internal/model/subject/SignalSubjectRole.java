package dev.ngb.backend.trust.internal.model.subject;

/**
 * How a subject relates to the observation that names it.
 *
 * <p>One login observation can be about an account, a device and an instrument at once; the role
 * says which is which.</p>
 */
public enum SignalSubjectRole {
    /** The subject the observation is chiefly about. */
    PRIMARY,
    /** The other party in the observed interaction. */
    COUNTERPARTY,
    /** The resource acted on. */
    RESOURCE,
    /** The instrument or destination involved. */
    INSTRUMENT,
    /** Contextual, such as the network the action came from. */
    CONTEXT;
}
