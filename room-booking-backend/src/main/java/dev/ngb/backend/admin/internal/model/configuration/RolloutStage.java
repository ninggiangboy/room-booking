package dev.ngb.backend.admin.internal.model.configuration;

/**
 * How far a stage of a rollout reaches.
 * <p>A rollback is a stage like any other, pointing at the stage it reverses, because the incident
 * review needs to see both the value that was live during the incident and what replaced it.</p>
 */
public enum RolloutStage {

    /** A small share of traffic, watched before anything wider. */
    CANARY,

    /** A larger share, still short of everybody. */
    PARTIAL,

    /** Everybody the change was approved for. */
    FULL,

    /** Returns to a named earlier value; history is never rewritten. */
    ROLLBACK
}
