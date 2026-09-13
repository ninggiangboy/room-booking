package dev.ngb.backend.model;

/**
 * The note kind of {@code case_notes}.
 */
public enum CaseNoteKind {

    /** Observation. */
    OBSERVATION,

    /** Handoff summary. */
    HANDOFF_SUMMARY,

    /** Investigation step. */
    INVESTIGATION_STEP,

    /** Policy reasoning. */
    POLICY_REASONING,

    /** Supervisor guidance. */
    SUPERVISOR_GUIDANCE,

    /** Quality remark. */
    QUALITY_REMARK,

    /** Safety note. */
    SAFETY_NOTE,

    /** Legal note. */
    LEGAL_NOTE
}
