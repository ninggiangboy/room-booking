package dev.ngb.backend.model;

/**
 * How two subjects came to be linked.
 *
 * <p>The omissions are the point: there is no device fingerprint, shared network address, shared
 * payment instrument or household inference here, and a vocabulary with no name for one cannot be
 * talked into it later.</p>
 */
public enum SubjectLinkMethod {

    /** The subject signed in, which is certain and carries no confidence. */
    AUTHENTICATION,

    /** The subject stated the link themselves. */
    EXPLICIT_DECLARATION,

    /** Two accounts were merged under an approved process. */
    ACCOUNT_MERGE
}
