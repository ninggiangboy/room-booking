package dev.ngb.backend.model;

/**
 * The eligibility state of {@code damage_claims}.
 */
public enum ClaimEligibilityState {

    /** Unassessed. */
    UNASSESSED,

    /** Eligible. */
    ELIGIBLE,

    /** Ineligible. */
    INELIGIBLE,

    /** Referred. */
    REFERRED
}
