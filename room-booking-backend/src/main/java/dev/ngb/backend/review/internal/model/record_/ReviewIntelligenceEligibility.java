package dev.ngb.backend.review.internal.model.record_;

/**
 * Whether derived intelligence may read this review.
 */
public enum ReviewIntelligenceEligibility {
    /** Not yet decided. */
    PENDING,
    /** Eligible for extraction and aggregation. */
    INCLUDED,
    /** Kept out, for example because moderation removed it. */
    EXCLUDED,
    /** Something changed and the derived values are stale. */
    REPROCESS_REQUIRED
}
