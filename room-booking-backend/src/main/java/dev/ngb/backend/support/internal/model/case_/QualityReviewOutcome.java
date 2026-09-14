package dev.ngb.backend.support.internal.model.case_;

/**
 * The overall outcome of {@code case_quality_reviews}.
 */
public enum QualityReviewOutcome {

    /** Satisfactory. */
    SATISFACTORY,

    /** Coaching required. */
    COACHING_REQUIRED,

    /** Policy defect. */
    POLICY_DEFECT,

    /** Tooling defect. */
    TOOLING_DEFECT,

    /** Re review required. */
    RE_REVIEW_REQUIRED,

    /** Superseding decision required. */
    SUPERSEDING_DECISION_REQUIRED
}
