package dev.ngb.backend.growth.internal.model.campaign;

/**
 * Where a growth campaign stands.
 *
 * <p>Leaving {@code DRAFT} is the point at which the campaign becomes something done to real
 * people, so it is the point at which the fairness review and the price-transparency attestation
 * are owed.</p>
 */
public enum CampaignStatus {

    /** Being written; nothing has been sent. */
    DRAFT,

    /** Reviewed and waiting for its send window. */
    SCHEDULED,

    /** Sending. */
    RUNNING,

    /** Stopped part-way without being cancelled. */
    PAUSED,

    /** Finished its window. */
    COMPLETED,

    /** Called off, for a recorded reason. */
    CANCELLED
}
