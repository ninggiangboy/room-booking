package dev.ngb.backend.stay.internal.model.task;

/**
 * The form a piece of task evidence takes.
 */
public enum TaskEvidenceType {
    /** A still image. */
    PHOTO,
    /** A recording. */
    VIDEO,
    /** A completed structured checklist. */
    CHECKLIST,
    /** A statement by the assignee. */
    ATTESTATION,
    /** A time, location or device reading. */
    DEVICE_SIGNAL,
    /** An uploaded document. */
    DOCUMENT,
    /** A report from an external service. */
    PROVIDER_REPORT
}
