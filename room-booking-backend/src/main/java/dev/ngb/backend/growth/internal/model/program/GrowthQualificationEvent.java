package dev.ngb.backend.growth.internal.model.program;

/**
 * What has to happen before a growth reward is owed.
 */
public enum GrowthQualificationEvent {

    /** Signing up is enough. */
    ACCOUNT_CREATED,

    /** The subject’s first confirmed booking. */
    FIRST_BOOKING_CONFIRMED,

    /** Any confirmed booking. */
    BOOKING_CONFIRMED,

    /** A stay that actually happened. */
    STAY_COMPLETED,

    /** A purchase whose payment settled. */
    PURCHASE_SETTLED,

    /** Somebody decided, on the record. */
    MANUAL_AWARD
}
