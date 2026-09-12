package dev.ngb.backend.model;

/**
 * How much evidence stands behind a price recommendation.
 *
 * <p>Deliberately coarse. A model's numeric confidence is not meaningful to a host, and presenting
 * one implies a precision the estimate does not have; three bands are enough to decide whether to
 * follow advice or look closer.</p>
 */
public enum RecommendationConfidence {
    /** Thin or unusual evidence; treat as a hint only. */
    LOW,
    /** Ordinary evidence for this listing and season. */
    MEDIUM,
    /** Strong, consistent evidence. */
    HIGH
}
