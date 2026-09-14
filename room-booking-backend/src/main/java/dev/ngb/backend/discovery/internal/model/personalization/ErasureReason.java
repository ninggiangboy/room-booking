package dev.ngb.backend.discovery.internal.model.personalization;

/**
 * Why derived personalization is being erased.
 */
public enum ErasureReason {

    /** Guest request. */
    GUEST_REQUEST,

    /** Opt out. */
    OPT_OUT,

    /** Account closure. */
    ACCOUNT_CLOSURE,

    /** Regulatory order. */
    REGULATORY_ORDER,

    /** Data quality defect. */
    DATA_QUALITY_DEFECT,

    /** Support action. */
    SUPPORT_ACTION
}
