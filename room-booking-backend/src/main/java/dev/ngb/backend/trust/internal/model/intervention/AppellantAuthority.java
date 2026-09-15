package dev.ngb.backend.trust.internal.model.intervention;

/**
 * On what basis somebody may appeal.
 */
public enum AppellantAuthority {
    /** The subject of the decision. */
    SUBJECT,
    /** The owner of the account concerned. */
    ACCOUNT_OWNER,
    /** Somebody acting with the subject's authority. */
    AUTHORIZED_AGENT,
    /** A legal guardian. */
    LEGAL_GUARDIAN
}
