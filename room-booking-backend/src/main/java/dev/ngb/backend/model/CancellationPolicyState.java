package dev.ngb.backend.model;

/**
 * Publication state of one immutable cancellation policy version.
 *
 * <p>Only a published version may be quoted to a guest. Publication freezes the rule document, the
 * evaluator version, and the cutoff semantics; retirement is the one move left afterwards, because a
 * version has to be able to stop applying without its past applications changing.</p>
 */
public enum CancellationPolicyState {
    /** Being written; not quotable. */
    DRAFT,
    /** Signed off and awaiting publication. */
    APPROVED,
    /** Quotable and settleable. Frozen from here. */
    PUBLISHED,
    /** No longer offered. Still settles the bookings that cited it. */
    RETIRED
}
