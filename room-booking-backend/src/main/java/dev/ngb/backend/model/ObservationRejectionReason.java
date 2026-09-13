package dev.ngb.backend.model;

/**
 * Why a provider statement was recorded but not applied.
 *
 * <p>A contradictory or late provider message is evidence about the provider, not something to discard.</p>
 */
public enum ObservationRejectionReason {

    /** It would move the claim backwards behind a newer outcome. */
    STALE_REGRESSION,

    /** The same provider event had already been applied. */
    DUPLICATE,

    /** It could not be normalized. */
    UNPARSEABLE,

    /** Its amounts contradict what the provider already told us. */
    AMOUNT_CONFLICT,

    /** It names a claim this platform does not hold. */
    UNKNOWN_CLAIM
}
