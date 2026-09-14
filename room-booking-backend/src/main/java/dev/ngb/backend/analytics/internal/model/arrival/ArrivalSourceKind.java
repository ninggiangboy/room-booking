package dev.ngb.backend.analytics.internal.model.arrival;

/**
 * Where an arrival came from.
 *
 * <p>The distinction decides what the row is allowed to claim: only change-data-capture carries a
 * source commit instant, and a backfill may never produce client-side evidence.</p>
 */
public enum ArrivalSourceKind {

    /** Republished from a source transaction, so it can state when that transaction committed. */
    OUTBOX_CDC,

    /** Reported by a client application and validated at the boundary. */
    CLIENT_COLLECTOR,

    /** Sent by an external provider. */
    PROVIDER_WEBHOOK,

    /** Produced by a transformation inside the platform. */
    INTERNAL_PIPELINE,

    /** Reconstructed from historical source rows; never used for client-side evidence. */
    BACKFILL
}
