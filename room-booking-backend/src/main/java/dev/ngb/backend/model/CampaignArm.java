package dev.ngb.backend.model;

/**
 * Whether somebody in a campaign audience is contacted or deliberately held out.
 *
 * <p>The holdout is the only evidence a campaign did anything; contacting it once turns the
 * measurement into a comparison of nothing.</p>
 */
public enum CampaignArm {

    /** Contacted. */
    TREATMENT,

    /** Deliberately not contacted, so the effect can be measured. */
    HOLDOUT
}
