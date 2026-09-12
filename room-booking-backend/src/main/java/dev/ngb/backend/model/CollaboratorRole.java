package dev.ngb.backend.model;

/**
 * What someone does on a property they do not own.
 *
 * <p>The role is a label for operational purpose, not a grant of authority: what a collaborator may
 * actually do lives in a property-scoped {@code capability_grants} row that the collaboration points
 * at, so ending a collaboration and revoking its authority stay one linked act.</p>
 */
public enum CollaboratorRole {
    /** Operates the listing on the owner's behalf. */
    CO_HOST,
    /** Handles turnover and cleaning tasks. */
    CLEANER,
    /** Handles repairs and maintenance tasks. */
    MAINTENANCE,
    /** Handles guest arrival at a staffed property. */
    FRONT_DESK,
    /** Read-only access. */
    VIEWER
}
