package dev.ngb.backend.model;

/**
 * The visibility scope of {@code case_notes}.
 */
public enum NoteVisibility {

    /** Internal only. */
    INTERNAL_ONLY,

    /** Supervisor only. */
    SUPERVISOR_ONLY,

    /** Legal only. */
    LEGAL_ONLY,

    /** Restricted authority. */
    RESTRICTED_AUTHORITY
}
