package dev.ngb.backend.model;

/**
 * The source kind of {@code external_claim_observations}.
 */
public enum ExternalClaimObservationSource {

    /** Webhook. */
    WEBHOOK,

    /** Query. */
    QUERY,

    /** Reconciliation. */
    RECONCILIATION,

    /** Manual. */
    MANUAL
}
