package dev.ngb.backend.support.internal.model.case_;

/**
 * The delivery state of {@code case_contacts}.
 */
public enum ContactDeliveryState {

    /** Not applicable. */
    NOT_APPLICABLE,

    /** Pending. */
    PENDING,

    /** Sent. */
    SENT,

    /** Delivered. */
    DELIVERED,

    /** Failed. */
    FAILED,

    /** Suppressed. */
    SUPPRESSED
}
