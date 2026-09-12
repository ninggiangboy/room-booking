package dev.ngb.backend.model;

/**
 * Whether metadata stripping or re-encoding still has to happen.
 */
public enum AttachmentTransformationState {
    /** Policy asks for no transformation. */
    NOT_REQUIRED,
    /** Queued. */
    PENDING,
    /** Done; the protected original is kept where evidence retention requires it. */
    COMPLETED,
    /** Transformation failed and the object stays unavailable. */
    FAILED
}
