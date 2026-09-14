package dev.ngb.backend.model;

/**
 * Where one person’s place in a campaign audience stands.
 */
public enum CampaignMembershipState {

    /** In the audience and not yet contacted. */
    ELIGIBLE,

    /** Held back, for a recorded reason. */
    SUPPRESSED,

    /** Has received at least one message. */
    CONTACTED,

    /** Did what the campaign asked. */
    CONVERTED,

    /** Removed from the audience. */
    EXCLUDED
}
