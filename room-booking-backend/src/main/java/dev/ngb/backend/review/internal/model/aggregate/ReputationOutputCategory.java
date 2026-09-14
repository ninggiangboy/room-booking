package dev.ngb.backend.review.internal.model.aggregate;

/**
 * What a contextual reputation view concluded.
 *
 * <p>Categories rather than a number on a scale, because the output is an input to a bounded decision
 * and not a public score. {@code INSUFFICIENT_EVIDENCE} carries a reason and no value.</p>
 */
public enum ReputationOutputCategory {
    /** Not enough behind it to answer. Not a low answer. */
    INSUFFICIENT_EVIDENCE,
    /** Below what the purpose expects. */
    BELOW_EXPECTATION,
    /** As expected. */
    MEETS_EXPECTATION,
    /** Better than expected. */
    ABOVE_EXPECTATION,
    /** The purpose does not apply to this subject. */
    NOT_APPLICABLE
}
