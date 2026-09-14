package dev.ngb.backend.support.internal.model.claim;

/**
 * The valuation method of {@code damage_claim_items}.
 */
public enum ValuationMethod {

    /** Reasonable repair cost. */
    REASONABLE_REPAIR_COST,

    /** Like kind less depreciation. */
    LIKE_KIND_LESS_DEPRECIATION,

    /** Actual cash value. */
    ACTUAL_CASH_VALUE,

    /** Capped cleaning. */
    CAPPED_CLEANING,

    /** Documented invoice. */
    DOCUMENTED_INVOICE,

    /** Provider determination. */
    PROVIDER_DETERMINATION
}
