package dev.ngb.backend.model;

/**
 * How an example came to be observed at all.
 *
 * <p>Fraud, moderation, support and ranking outcomes are normally visible only for the cases some
 * earlier system selected, and a dataset built without knowing that reproduces the earlier system's
 * blind spot with more confidence.</p>
 */
public enum LabelObservationBasis {

    /** Observed in the ordinary course of the system's behaviour. */
    ORGANIC,

    /** Observed because the example was sampled for audit regardless of any score. */
    RANDOMIZED_AUDIT,

    /** Observed because the serving policy deliberately explored off its own ranking. */
    EXPLORATION,

    /** Observed because a person reviewed it, with their role, policy and confidence. */
    HUMAN_REVIEW
}
