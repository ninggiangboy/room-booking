package dev.ngb.backend.identity.internal.model.capability;

/**
 * Why a capability grant exists.
 *
 * <p>The source is what makes a grant reviewable. A capability a user gave themselves and one a risk
 * decision imposed are very different facts, and an operator reviewing an account needs to tell them
 * apart without reading a free-text reason.</p>
 *
 * <p>{@code LEGACY} existed only to mark grants backfilled from the retired {@code user_roles}
 * table's role model. Migration {@code 037} converted every such row to {@code SELF_SERVICE} and
 * narrowed the database check constraint accordingly, so this enum no longer declares it.</p>
 */
public enum GrantSource {
    /** The principal granted it to themselves by completing a normal flow. */
    SELF_SERVICE,
    /** Delegated by another principal, such as an organization owner to a co-host. */
    DELEGATION,
    /** Conferred by a completed compliance or verification step. */
    COMPLIANCE,
    /** Imposed or narrowed by a risk decision. */
    RISK,
    /** Granted by platform governance, such as an operator role. */
    GOVERNANCE
}
