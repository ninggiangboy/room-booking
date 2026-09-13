package dev.ngb.backend.model;

/**
 * What sort of subject a pseudonym stands for.
 */
public enum SubjectKind {

    /** One declared first-party session with no account behind it. */
    ANONYMOUS_SESSION,

    /** One first-party application installation. */
    DEVICE_INSTALLATION,

    /** A signed-in account holder. */
    AUTHENTICATED_SUBJECT
}
