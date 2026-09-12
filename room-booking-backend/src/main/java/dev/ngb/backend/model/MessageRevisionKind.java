package dev.ngb.backend.model;

/**
 * Why a revision was written beside a message.
 */
public enum MessageRevisionKind {
    /** The author replaced the content; the original stays readable as evidence. */
    CORRECTION,
    /** The author took the message back from ordinary presentation. */
    WITHDRAWAL,
    /** Policy replaced what ordinary recipients see, such as masked contact details. */
    MODERATION_PROJECTION,
    /** Retention or privacy policy removed part of the content. */
    REDACTION
}
