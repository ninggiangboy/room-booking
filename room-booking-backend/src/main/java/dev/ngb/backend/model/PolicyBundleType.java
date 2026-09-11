package dev.ngb.backend.model;

/**
 * Kind of approved rule set a market policy bundle carries.
 *
 * <p>One bundle of each type is in force per market at any instant, enforced by
 * {@code ex_market_policy_bundles_no_overlap}. Splitting rules by type lets tax be re-approved
 * without re-approving the cancellation policy.</p>
 */
public enum PolicyBundleType {
    /** What may be sold, to whom, and under which commercial model. */
    PRODUCT,
    /** Contracting entity, consumer rights, and marketplace legal model. */
    LEGAL,
    /** Place and time of supply, rates, exemptions, withholding, and invoicing. */
    TAX,
    /** Lawful basis, purpose limitation, and cross-border transfer rules. */
    PRIVACY,
    /** How long each class of record is kept before erasure. */
    RETENTION,
    /** Approved cancellation ladders and their deadlines. */
    CANCELLATION,
    /** Mandatory pre-contract and pre-payment disclosures. */
    DISCLOSURE,
    /** Payout eligibility, timing, holds, and rails. */
    PAYOUT
}
