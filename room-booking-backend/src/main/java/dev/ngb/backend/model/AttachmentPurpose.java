package dev.ngb.backend.model;

/**
 * What an uploaded object is for, which drives retention and who may download it.
 */
public enum AttachmentPurpose {
    /** A photograph or video shared in conversation. */
    MEDIA,
    /** A document shared between the parties. */
    DOCUMENT,
    /** Material retained for a dispute, incident or claim. */
    EVIDENCE,
    /** Arrival or access material attached to an instruction set. */
    INSTRUCTION
}
