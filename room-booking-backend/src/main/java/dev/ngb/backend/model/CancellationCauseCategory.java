package dev.ngb.backend.model;

/**
 * Why a cancellation is happening, at the level that changes who pays.
 *
 * <p>The reason code beside it is free-form and catalogued; this is the coarse class the funding rules
 * are written against. A guest changing their mind and a typhoon are the same action with entirely
 * different bills.</p>
 */
public enum CancellationCauseCategory {
    /** The guest decided to cancel. */
    GUEST_CHOICE,
    /** The host decided to cancel. */
    HOST_CHOICE,
    /** The platform ended the stay, such as after a policy breach. */
    PLATFORM_ACTION,
    /** An approved circumstance outside anybody's control. */
    EXTENUATING,
    /** The stay was unsafe to proceed with. */
    SAFETY,
    /** The booking or the supply turned out to be fraudulent. */
    FRAUD,
    /** The property could not deliver what was sold. */
    SUPPLY_FAILURE,
    /** The money was never collected. */
    PAYMENT_FAILURE
}
