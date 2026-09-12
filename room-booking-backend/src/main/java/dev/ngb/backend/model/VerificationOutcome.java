package dev.ngb.backend.model;

/**
 * What a completed verification case concluded.
 *
 * <p>{@link #INCONCLUSIVE} is deliberately not a failure. Treating "we could not tell" as "you
 * failed" would penalise hosts for a provider's limitations, and eligibility policy is entitled to
 * respond differently to the two.</p>
 */
public enum VerificationOutcome {
    /** The evidence supported the claim. */
    PASSED,
    /** The evidence contradicted the claim. */
    FAILED,
    /** The evidence settled nothing either way. */
    INCONCLUSIVE
}
