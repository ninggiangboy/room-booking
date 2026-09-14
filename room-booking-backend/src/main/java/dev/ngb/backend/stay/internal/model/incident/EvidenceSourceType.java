package dev.ngb.backend.stay.internal.model.incident;

/**
 * What kind of artifact an evidence link points at.
 */
public enum EvidenceSourceType {
    /** One conversation message. */
    MESSAGE,
    /** One attachment. */
    MESSAGE_ATTACHMENT,
    /** One piece of task evidence. */
    TASK_EVIDENCE,
    /** One stay observation. */
    STAY_OBSERVATION,
    /** One provider access observation. */
    ACCESS_OBSERVATION,
    /** One maintenance record. */
    MAINTENANCE_RECORD,
    /** A listing as it stood. */
    LISTING_SNAPSHOT,
    /** A booking as it stood. */
    BOOKING_SNAPSHOT,
    /** An internal support note. */
    SUPPORT_NOTE,
    /** An uploaded still image. */
    PHOTO,
    /** An uploaded recording. */
    VIDEO,
    /** Metadata about a call. */
    CALL_METADATA
}
