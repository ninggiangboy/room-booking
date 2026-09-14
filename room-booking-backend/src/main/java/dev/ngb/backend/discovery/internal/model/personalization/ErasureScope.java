package dev.ngb.backend.discovery.internal.model.personalization;

/**
 * How much derived personalization an erasure directive reaches.
 */
public enum ErasureScope {

    /** Recent activity. */
    RECENT_ACTIVITY,

    /** Behavioral profile. */
    BEHAVIORAL_PROFILE,

    /** All derived personalization. */
    ALL_DERIVED_PERSONALIZATION,

    /** Account deletion. */
    ACCOUNT_DELETION
}
