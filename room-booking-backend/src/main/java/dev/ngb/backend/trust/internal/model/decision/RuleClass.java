package dev.ngb.backend.trust.internal.model.decision;

/**
 * How much authority one rule has over the answer.
 *
 * <p>An explanation-only rule may describe the outcome but never move it, which is checked rather
 * than trusted. This is the structural half of keeping a stale optional model from escalating an
 * action beyond its deterministic fallback.</p>
 */
public enum RuleClass {
    /** Must be obeyed; a legal or safety prohibition. */
    MANDATORY,
    /** May influence the outcome within policy. */
    ADVISORY,
    /** Recorded for explanation; contributes nothing. */
    EXPLANATION_ONLY;
}
