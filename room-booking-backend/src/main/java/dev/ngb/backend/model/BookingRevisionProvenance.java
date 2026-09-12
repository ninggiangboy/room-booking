package dev.ngb.backend.model;

/**
 * How much evidence stands behind a revision's terms.
 *
 * <p>A revision the platform evaluated can be reproduced from its inputs. One imported from a channel
 * manager cannot, and one a support agent typed in is weaker still. Recording the difference stops a
 * later reader treating all three as equally provable.</p>
 */
public enum BookingRevisionProvenance {
    /** Produced by the evaluator from a quote and a policy version. */
    PLATFORM_EVALUATED,
    /** Received from an external system; its arithmetic is not ours. */
    CHANNEL_IMPORTED,
    /** Entered by an agent under delegated authority. */
    SUPPORT_ENTERED,
    /** Reconstructed from incomplete history; never to be presented as exact. */
    LEGACY_UNRESOLVED
}
