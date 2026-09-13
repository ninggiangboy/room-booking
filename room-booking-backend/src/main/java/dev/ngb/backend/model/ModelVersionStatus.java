package dev.ngb.backend.model;

/**
 * Lifecycle of a registered model version.
 *
 * <p>Only an approved version reaches SHADOW, CANARY or ACTIVE. REJECTED and RETIRED are terminal:
 * returning one to service means registering a new version.</p>
 */
public enum ModelVersionStatus {

    /** Registered, not yet trained. */
    DRAFT,

    /** An artifact exists; nothing about the version may change from here on. */
    TRAINED,

    /** Evaluated against a baseline on a dataset whose leakage checks passed. */
    VALIDATED,

    /** Every function the impact class requires has signed, none of them the owner. */
    APPROVED,

    /** Producing predictions nobody acts on, to compare against the live path. */
    SHADOW,

    /** Carrying a bounded share of real traffic under guardrails. */
    CANARY,

    /** The champion for its scope. */
    ACTIVE,

    /** Refused at review; terminal. */
    REJECTED,

    /** Withdrawn from service; terminal, and blocks new predictions. */
    RETIRED,

    /** Replaced in the routing path by an earlier version or the deterministic fallback. */
    ROLLED_BACK
}
