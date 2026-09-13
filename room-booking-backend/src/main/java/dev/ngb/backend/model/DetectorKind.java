package dev.ngb.backend.model;

/**
 * What sort of detector produced an assessment.
 */
public enum DetectorKind {
    /** A rule or pattern. */
    DETERMINISTIC,
    /** A trained classifier. */
    CLASSIFIER,
    /** A malware scanner. */
    MALWARE,
    /** A link canonicalization and reputation check. */
    LINK_CHECK,
    /** A person reading the content. */
    HUMAN;
}
