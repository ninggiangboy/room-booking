package dev.ngb.backend.model;

/**
 * The domain that owns the command a risk decision advises about.
 *
 * <p>Risk advises; domains enforce. Nothing in the trust and safety schema writes inventory, money,
 * sessions or publication, so this names who did.</p>
 */
public enum RiskEnforcementDomain {
    /** Accounts, sessions and credentials. */
    IDENTITY,
    /** Listing content and publication. */
    LISTING,
    /** Availability, holds and claims. */
    INVENTORY,
    /** The booking contract. */
    BOOKING,
    /** Provider payment movement. */
    PAYMENT,
    /** Ledger, payable ownership and payout. */
    FINANCE,
    /** Conversations and notifications. */
    MESSAGING,
    /** Reviews and reputation. */
    REVIEW,
    /** Promotions, referrals and credits. */
    PROMOTION,
    /** Support cases and remedies. */
    SUPPORT,
    /** Access, tasks and incidents during a stay. */
    STAY_OPERATIONS,
    /** Administrative and governance commands. */
    ADMIN;
}
