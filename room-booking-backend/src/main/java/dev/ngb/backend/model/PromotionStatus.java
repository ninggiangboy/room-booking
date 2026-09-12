package dev.ngb.backend.model;

/**
 * Whether a promotion can currently be redeemed.
 *
 * <p>{@code EXHAUSTED} is distinct from {@code ENDED} on purpose: a campaign that ran out of budget
 * and one that reached its end date need different answers to the guest and different follow-up from
 * the team that funded it.</p>
 */
public enum PromotionStatus {
    /** Being prepared; no version is published. */
    DRAFT,
    /** Published but not yet inside its booking window. */
    SCHEDULED,
    /** Redeemable now. */
    ACTIVE,
    /** Temporarily suspended without being closed. */
    PAUSED,
    /** Stopped because its budget is fully committed. */
    EXHAUSTED,
    /** Closed, by date or by decision. */
    ENDED
}
