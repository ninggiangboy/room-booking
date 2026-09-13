package dev.ngb.backend.model;

/**
 * Whether a protected action may be evaluated.
 *
 * <p>A decision naming a draft or retired action is refused by trigger, so an action must be
 * deliberately opened before anything can be decided about it.</p>
 */
public enum RiskActionStatus {
    /** Registered but not yet open for evaluation. */
    DRAFT,
    /** Open for evaluation. */
    ACTIVE,
    /** No longer evaluated; historical decisions keep their meaning. */
    RETIRED;
}
