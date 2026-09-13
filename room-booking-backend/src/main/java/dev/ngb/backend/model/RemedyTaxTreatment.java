package dev.ngb.backend.model;

/**
 * The tax treatment of {@code case_remedy_lines}.
 */
public enum RemedyTaxTreatment {

    /** Not applicable. */
    NOT_APPLICABLE,

    /** Price adjusting. */
    PRICE_ADJUSTING,

    /** Non price adjusting. */
    NON_PRICE_ADJUSTING,

    /** Requires tax decision. */
    REQUIRES_TAX_DECISION
}
