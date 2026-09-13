package dev.ngb.backend.model;

/**
 * Which check was run against a proposed change.
 * <p>Schema checks the shape, policy checks whether it is allowed at all, simulation runs it
 * against
 * recorded traffic, preview renders what a user would see, a dry run executes it without
 * committing,
 * and conflict looks for another request moving the same thing. They are separate because they fail
 * for separate reasons.</p>
 */
public enum ChangeValidationKind {

    /** The proposed value has the shape its schema describes. */
    SCHEMA,

    /** The proposed value is one policy permits at all. */
    POLICY,

    /** The change was run against recorded traffic to see what it would do. */
    SIMULATION,

    /** The change was rendered as a user would see it. */
    PREVIEW,

    /** The change was executed without committing anything. */
    DRY_RUN,

    /** Nothing else in flight is moving the same thing. */
    CONFLICT
}
