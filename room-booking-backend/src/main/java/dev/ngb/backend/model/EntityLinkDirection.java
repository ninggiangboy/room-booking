package dev.ngb.backend.model;

/**
 * Whether the relationship has a direction.
 *
 * <p>An undirected pair is stored once in a canonical endpoint order. Without that the same
 * relationship can exist twice with two different confidences.</p>
 */
public enum EntityLinkDirection {
    /** Symmetric; stored with the lower endpoint first. */
    UNDIRECTED,
    /** From the first endpoint to the second. */
    A_TO_B,
    /** From the second endpoint to the first. */
    B_TO_A;
}
