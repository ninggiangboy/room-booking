package dev.ngb.backend.model;

/**
 * What a contact channel is used for.
 *
 * <p>Separating purposes is what lets a guest withdraw marketing consent without losing the booking
 * confirmations they still need to receive.</p>
 */
public enum ContactChannelPurpose {
    /** Security and account-lifecycle messages. */
    ACCOUNT,
    /** Invoices and settlement correspondence. */
    BILLING,
    /** Stay and operational messages. */
    OPERATIONS,
    /** Promotional messages, subject to consent. */
    MARKETING
}
