package dev.ngb.backend.support.internal.model.remedy;

/**
 * The document requirement of {@code remedy_catalog_versions}.
 */
public enum DocumentRequirement {

    /** None. */
    NONE,

    /** Receipt. */
    RECEIPT,

    /** Invoice. */
    INVOICE,

    /** Estimate. */
    ESTIMATE,

    /** Provider determination. */
    PROVIDER_DETERMINATION
}
