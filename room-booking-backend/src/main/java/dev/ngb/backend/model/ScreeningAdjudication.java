package dev.ngb.backend.model;

/**
 * What a person concluded about a screening match.
 *
 * <p>Recorded with the adjudicator and the instant, because a decision to let a flagged host keep
 * selling is exactly the decision a regulator will later ask about.</p>
 */
public enum ScreeningAdjudication {
    /** The match was a different person or entity. */
    FALSE_POSITIVE,
    /** The match is the host; consequences follow. */
    TRUE_MATCH,
    /** The reviewer could not determine which. */
    INCONCLUSIVE
}
