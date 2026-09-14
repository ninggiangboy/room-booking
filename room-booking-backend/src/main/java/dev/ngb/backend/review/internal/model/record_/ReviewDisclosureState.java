package dev.ngb.backend.review.internal.model.record_;

/**
 * Where a review stands in its cycle's disclosure.
 */
public enum ReviewDisclosureState {
    /** Written but not disclosable. */
    SEALED,
    /** Conditions are met and the cycle has not acted yet. */
    REVEAL_ELIGIBLE,
    /** The cycle revealed; visibility now depends on moderation. */
    REVEALED
}
