package dev.ngb.backend.trust.internal.model.decision;

/**
 * What happened when a rule was evaluated.
 *
 * <p>Only a match may contribute to the outcome. A rule that errored or was skipped contributed
 * nothing, so a failed evaluation cannot be presented afterwards as a finding.</p>
 */
public enum RuleHitResult {
    /** The rule fired. */
    MATCHED,
    /** The rule was evaluated and did not fire. */
    NOT_MATCHED,
    /** Not applicable to this evaluation. */
    SKIPPED,
    /** Evaluation failed. */
    ERRORED,
    /** Required inputs were unavailable. */
    INPUTS_MISSING
}
