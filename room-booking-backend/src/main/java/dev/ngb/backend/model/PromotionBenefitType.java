package dev.ngb.backend.model;

/**
 * The shape of what a promotion gives away.
 *
 * <p>Each shape carries exactly the parameters it needs, and the database refuses the others: a
 * percentage benefit that also names a fixed amount has no defined order of application, and two
 * evaluations of it could legitimately disagree.</p>
 */
public enum PromotionBenefitType {
    /** Takes a percentage off the qualifying amount, optionally capped. */
    PERCENT_OFF,
    /** Takes a fixed amount off, in a named currency. */
    FIXED_AMOUNT_OFF,
    /** Grants whole nights described by the eligibility payload. */
    FREE_NIGHTS,
    /** Waives one or more fees rather than reducing the nightly rate. */
    FEE_WAIVER
}
