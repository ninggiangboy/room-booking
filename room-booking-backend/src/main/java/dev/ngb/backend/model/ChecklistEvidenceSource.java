package dev.ngb.backend.model;

/**
 * Where the platform looks to decide whether a checklist item is outstanding.
 */
public enum ChecklistEvidenceSource {

    /** Listing content. */
    LISTING_CONTENT,

    /** Listing media. */
    LISTING_MEDIA,

    /** Review aspect. */
    REVIEW_ASPECT,

    /** Quality profile. */
    QUALITY_PROFILE,

    /** Response metric. */
    RESPONSE_METRIC,

    /** Market benchmark. */
    MARKET_BENCHMARK,

    /** Policy registry. */
    POLICY_REGISTRY
}
