package dev.ngb.backend.growth.internal.model.program;

/**
 * What a versioned eligibility rule decided about one subject.
 *
 * <p>Everything but {@code ELIGIBLE} carries an approved reason code, so a guest asking why they
 * did not qualify is answered from the row that decided it.</p>
 */
public enum GrowthEligibilityOutcome {

    /** The rules said yes. */
    ELIGIBLE,

    /** The rules said no. */
    INELIGIBLE,

    /** Eligible in principle, but a limit had already been reached. */
    CAPPED,

    /** Eligible in principle, but outside the dates the terms cover. */
    OUT_OF_WINDOW,

    /** Held back for a reason outside the eligibility rules themselves. */
    SUPPRESSED
}
