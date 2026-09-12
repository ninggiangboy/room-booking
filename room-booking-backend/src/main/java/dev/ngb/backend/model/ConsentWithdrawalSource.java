package dev.ngb.backend.model;

/**
 * How a consent was withdrawn.
 *
 * <p>A provider complaint counts: somebody marking mail as spam is a withdrawal whether or not
 * they used the platform to say so.</p>
 */
public enum ConsentWithdrawalSource {
    /** In the notification settings. */
    PREFERENCE_CENTRE,
    /** Recorded by an agent. */
    SUPPORT,
    /** Through a link in a delivered notice. */
    UNSUBSCRIBE_LINK,
    /** Reported by the delivery provider as a complaint. */
    PROVIDER_COMPLAINT,
    /** Implied by closing the account. */
    ACCOUNT_CLOSURE
}
