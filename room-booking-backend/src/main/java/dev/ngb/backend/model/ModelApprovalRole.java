package dev.ngb.backend.model;

/**
 * Which function recorded a decision on a model version.
 */
public enum ModelApprovalRole {

    /** The team whose decisions the model will inform. */
    DOMAIN,

    /** Reviews thresholds, error costs and abuse resistance. */
    RISK,

    /** Reviews purpose, lawful basis and what the model may see. */
    PRIVACY,

    /** Reviews artifact custody, access and provider exposure. */
    SECURITY,

    /** Reviews obligations the use creates. */
    LEGAL,

    /** Reviews financial exposure and accounting impact. */
    FINANCE,

    /** The owning team, which cannot count towards the approvals it needs. */
    ML_OWNER
}
