package dev.ngb.backend.model;

/**
 * Which domain holds the content itself.
 *
 * <p>Text and media stay where they are written. What lives in the trust and safety schema is a
 * stable identity, a digest and the decisions taken about it.</p>
 */
public enum ContentOwningDomain {
    /** Listing text and media. */
    LISTING,
    /** Messages and attachments. */
    MESSAGING,
    /** Reviews, responses and review media. */
    REVIEW,
    /** Profile fields and profile media. */
    IDENTITY,
    /** Support notes. */
    SUPPORT,
    /** Incident reports. */
    STAY_OPERATIONS;
}
