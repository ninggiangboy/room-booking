package dev.ngb.backend.model;

/**
 * Whether the words could be produced from the allowlisted facts.
 *
 * <p>A missing required variable fails into an operations queue. It is never filled in by a model,
 * which is why the blocked outcome has to name what was missing.</p>
 */
public enum RenderState {
    /** Produced, hashed and stored. */
    RENDERED,
    /** Rendering errored; the error code is recorded. */
    FAILED,
    /** A required variable was absent; the names are recorded. */
    BLOCKED_MISSING_VARIABLE
}
