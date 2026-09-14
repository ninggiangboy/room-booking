package dev.ngb.backend.support.internal.model.claim;

/**
 * The supporting document kind of {@code damage_claim_items}.
 */
public enum SupportingDocumentKind {

    /** Receipt. */
    RECEIPT,

    /** Invoice. */
    INVOICE,

    /** Estimate. */
    ESTIMATE,

    /** Photo only. */
    PHOTO_ONLY,

    /** Inspection report. */
    INSPECTION_REPORT,

    /** None. */
    NONE
}
