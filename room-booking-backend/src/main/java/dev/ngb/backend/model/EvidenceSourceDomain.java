package dev.ngb.backend.model;

/**
 * Which domain owns the artifact an evidence link points at.
 */
public enum EvidenceSourceDomain {
    /** Conversations and their attachments. */
    MESSAGING,
    /** Tasks, observations and their evidence. */
    OPERATIONS,
    /** Grants and provider observations. */
    ACCESS,
    /** Listing content and media. */
    LISTING,
    /** Booking facts and snapshots. */
    BOOKING,
    /** Payment records. */
    PAYMENTS,
    /** Support notes and case material. */
    SUPPORT
}
