package dev.ngb.backend.model;

/**
 * Kind of contact channel a principal has registered.
 *
 * <p>Exclusivity follows proof, not claim: two accounts may each register the same address, but only
 * a verified primary channel is unique across accounts.</p>
 */
public enum ContactChannelType {
    /** An email address. */
    EMAIL,
    /** A telephone number. */
    PHONE,
    /** A push-notification destination. */
    PUSH
}
