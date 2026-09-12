package dev.ngb.backend.model;

/**
 * What a provider or bank said about a transfer, in the platform's vocabulary.
 *
 * <p>Native provider states are mapped here without discarding the original evidence. The mapping is
 * deliberately conservative: anything that does not clearly mean the money arrived is not
 * {@link #PAID}.</p>
 */
public enum NormalizedPayoutState {
    /** The provider took the request. */
    ACCEPTED,
    /** The transfer is moving through the rail. */
    IN_TRANSIT,
    /** The verified finality condition was met. */
    PAID,
    /** The transfer will not complete. */
    FAILED,
    /** It completed and the receiving bank sent it back. */
    RETURNED,
    /** It was stopped before sending. */
    CANCELLED,
    /** The evidence does not say. */
    UNKNOWN
}
