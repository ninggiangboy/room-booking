package dev.ngb.backend.model;

/**
 * Why a listing occupied the slot it occupied.
 *
 * <p>Organic relevance, a bounded exploration draw, and paid placement are three different things and
 * are never recorded as one.</p>
 */
public enum ExposureReason {

    /** Earned its slot on relevance. */
    ORGANIC,

    /** Given a slot from a bounded exploration budget to gather evidence. */
    EXPLORATION,

    /** Paid placement, always labelled. */
    SPONSORED,

    /** Placed deliberately by an operator decision. */
    PINNED
}
