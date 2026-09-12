package dev.ngb.backend.model;

/**
 * Why a guest needs to be moved.
 *
 * <p>The cause drives who funds the move, which is why it is a vocabulary rather than a free-text
 * note. A host cancelling late and a flood are not the same bill.</p>
 */
public enum RelocationCause {
    /** The host cancelled, leaving the guest without a stay. */
    HOST_CANCELLATION,
    /** The property cannot be occupied. */
    PROPERTY_UNAVAILABLE,
    /** The stay is unsafe to proceed with. */
    SAFETY,
    /** The property is materially not what was sold. */
    MISREPRESENTATION,
    /** The supply was sold twice. */
    OVERBOOKING,
    /** An external event made the stay impossible. */
    NATURAL_EVENT,
    /** An authority stopped the stay. */
    REGULATORY
}
