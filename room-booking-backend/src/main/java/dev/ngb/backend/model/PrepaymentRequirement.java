package dev.ngb.backend.model;

/**
 * When a guest has to pay under a rate plan.
 *
 * <p>This decides what the payment domain is asked to collect and when, so it is a property of the
 * offer rather than a checkout preference the guest picks at the last moment.</p>
 */
public enum PrepaymentRequirement {
    /** The whole amount is collected at booking. */
    FULL_PREPAYMENT,
    /** Part is collected at booking, the balance later. */
    DEPOSIT,
    /** Nothing is collected by the platform; the guest pays the property. */
    PAY_AT_PROPERTY,
    /** The amount is collected in scheduled instalments. */
    INSTALMENTS
}
