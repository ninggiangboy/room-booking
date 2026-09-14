package dev.ngb.backend.trust.internal.model.content;

/**
 * Whether the reported party may learn who reported them.
 *
 * <p>Confidential by default. Anything else needs a separately recorded authorization, because a
 * reporter whose identity leaks is a reporter who will not report next time.</p>
 */
public enum ReporterDisclosure {
    /** The reporter is not identified to anyone outside the platform. */
    CONFIDENTIAL,
    /** Identifiable to a public authority under an authorization. */
    AUTHORITY_ONLY,
    /** Disclosed under a recorded authorization. */
    DISCLOSED;
}
