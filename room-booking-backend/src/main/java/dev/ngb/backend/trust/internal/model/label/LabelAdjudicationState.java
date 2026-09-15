package dev.ngb.backend.trust.internal.model.label;

/**
 * How settled a label is.
 *
 * <p>Only a confirmed label with evidence behind it may be trained on, and a reversal supersedes
 * rather than coexisting: two contradictory answers in a training set are worse than neither.</p>
 */
public enum LabelAdjudicationState {
    /** Believed but not established. */
    PROVISIONAL,
    /** Established, with evidence. */
    CONFIRMED,
    /** Contested and under review. */
    DISPUTED,
    /** Withdrawn in favour of a corrected label. */
    REVERSED,
    /** Too old to represent current behaviour. */
    EXPIRED_FOR_TRAINING
}
