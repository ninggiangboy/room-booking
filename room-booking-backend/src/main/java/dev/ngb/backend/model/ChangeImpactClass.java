package dev.ngb.backend.model;

/**
 * What a change reaches, which is what decides how it has to be approved.
 * <p>A value touching money, identity, safety or a regulator is never changed by one person,
 * whatever the setting would prefer.</p>
 */
public enum ChangeImpactClass {

    /** Changes only what something looks like. */
    COSMETIC,

    /** Changes how the platform runs without reaching money or eligibility. */
    OPERATIONAL,

    /** Changes what somebody is charged or paid. */
    PRICING,

    /** Reaches the ledger, payouts or settlement. */
    FINANCIAL,

    /** Reaches who somebody is or what they are allowed to be. */
    IDENTITY,

    /** Reaches whether somebody is protected from harm. */
    SAFETY,

    /** Reaches an obligation the platform owes a regulator. */
    REGULATORY
}
