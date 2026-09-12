package dev.ngb.backend.model;

/**
 * Progress of one refund's claim on one capture.
 *
 * <p>Reservations are what stop two concurrent partial refunds from exceeding the money a capture
 * actually holds.</p>
 */
public enum RefundAllocationState {
    /** Amount held against the capture, nothing submitted. */
    RESERVED,
    /** A refund operation exists for this allocation. */
    SUBMITTED,
    /** The allocated amount was returned. */
    SETTLED,
    /** The reservation was given back to the refundable pool. */
    RELEASED,
    /** The allocation's operation failed with no money moved. */
    FAILED
}
