package dev.ngb.backend.model;

/**
 * What a reviewed appeal concluded.
 *
 * <p>An {@link #OVERTURNED} appeal must produce a new eligibility decision, enforced by
 * {@code ck_verification_appeals_overturn}. Without one, nothing actually changed for the host and
 * the capability they appealed about stays exactly as it was.</p>
 */
public enum AppealOutcome {
    /** The original decision stands. */
    UPHELD,
    /** The original decision was wrong and is replaced. */
    OVERTURNED,
    /** Part of the original decision was replaced. */
    PARTIAL
}
