package dev.ngb.backend.growth.internal.model.affiliate;

/**
 * Where an affiliate agreement stands.
 */
public enum AffiliatePartnerStatus {

    /** Terms being agreed; nothing is attributable yet. */
    DRAFT,

    /** Attributing clicks and earning commission. */
    ACTIVE,

    /** Stopped pending a decision, for a recorded reason. */
    SUSPENDED,

    /** Finished. */
    TERMINATED
}
