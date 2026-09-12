package dev.ngb.backend.model;

/**
 * What must be true of a component before inventory is confirmed.
 *
 * <p>Payment reports the facts; booking evaluates the condition. Recorded here so that a later
 * argument about why a stay was confirmed reads the rule that applied at the time.</p>
 */
public enum ConfirmationCondition {
    /** The whole obligation has been captured. */
    FULL_AMOUNT_CAPTURED,
    /** This component alone has been captured. */
    THIS_COMPONENT_CAPTURED,
    /** The whole obligation is reserved at the provider. */
    FULL_AMOUNT_AUTHORIZED,
    /** A reusable mandate or token has been validated for later collection. */
    MANDATE_VALIDATED,
    /** Confirmation granted against an explicitly owned risk. */
    DELAYED_PAYMENT_ACCEPTED,
    /** This component does not gate confirmation. */
    NONE
}
