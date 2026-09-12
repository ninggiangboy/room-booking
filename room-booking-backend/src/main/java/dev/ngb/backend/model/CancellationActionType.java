package dev.ngb.backend.model;

/**
 * What a cancellation preview is calculating.
 *
 * <p>Narrower than {@link CancellationDecisionType}: a preview never corrects an earlier decision,
 * because there is nothing to show a guest about a correction they did not ask for.</p>
 */
public enum CancellationActionType {
    /** Ending the whole stay. */
    FULL_CANCELLATION,
    /** Ending part of it, such as some nights or some units. */
    PARTIAL_CANCELLATION,
    /** Changing the terms rather than ending them. */
    MODIFICATION,
    /** The guest never arrived. */
    NO_SHOW,
    /** Moving the guest to different supply. */
    RELOCATION
}
