package dev.ngb.backend.admin.internal.model.change;

/**
 * How a feature flag state came to be set.
 * <p>The asymmetry is the point: pulling a switch and letting a flag expire both turn things off
 * and
 * need nobody, while turning a flag on is always a change somebody approved.</p>
 */
public enum FeatureFlagStateOrigin {

    /** The first state a flag was registered with, matching its declared default. */
    BOOTSTRAP,

    /** Set under an approved request. */
    CHANGE_REQUEST,

    /** Pulled in an emergency; it can only ever turn something off. */
    KILL_SWITCH_PULL,

    /** Returned to an earlier state under an approved request. */
    ROLLBACK,

    /** Turned off because the flag reached the date its owner agreed to. */
    EXPIRY
}
