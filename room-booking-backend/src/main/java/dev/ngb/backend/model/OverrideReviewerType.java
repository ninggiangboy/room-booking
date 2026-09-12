package dev.ngb.backend.model;

/**
 * Who judged one booking against an override programme.
 *
 * <p>Anything other than {@code AUTOMATED} must name the person. "An agent approved it" without an
 * agent is not a record of a decision.</p>
 */
public enum OverrideReviewerType {
    /** Decided by rule, with no human in the path. */
    AUTOMATED,
    /** A support agent. */
    AGENT,
    /** A trained reviewer for this programme. */
    SPECIALIST,
    /** Legal review, where the programme requires it. */
    LEGAL
}
