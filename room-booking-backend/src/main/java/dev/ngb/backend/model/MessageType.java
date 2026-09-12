package dev.ngb.backend.model;

/**
 * The envelope a message carries.
 *
 * <p>Clients submit only author content. Delivery, moderation, translation and action-authority
 * fields are server-set, which is why the types a client may write and the types the platform
 * writes are distinguished here rather than by a flag.</p>
 */
public enum MessageType {
    /** Author-written UTF-8 content with a detected or declared language. */
    TEXT,
    /** An image or video referencing approved object metadata. */
    MEDIA,
    /** A document referencing approved object metadata. */
    ATTACHMENT,
    /** Rendered by the platform from a committed domain event. */
    SYSTEM_FACT,
    /** A server-issued action type and resource reference; never an amount or a transition. */
    STRUCTURED_ACTION,
    /** A pointer to an instruction version, with no secrets embedded. */
    INSTRUCTION_UPDATE,
    /** A pointer to a user-safe incident projection. */
    INCIDENT_UPDATE
}
