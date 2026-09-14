package dev.ngb.backend.support.internal.model.decision;

/**
 * What an authorized interpretation concluded.
 *
 * <p>{@code INCONCLUSIVE} is a real answer and is never coerced into claimant or respondent fault.</p>
 */
public enum FindingOutcome {

    /** The evidence meets the proof standard for the question asked. */
    SUPPORTED,

    /** The evidence does not meet it. */
    NOT_SUPPORTED,

    /** The question could not be answered either way; the gap is named rather than guessed. */
    INCONCLUSIVE,

    /** The question does not arise on this case. */
    NOT_APPLICABLE
}
