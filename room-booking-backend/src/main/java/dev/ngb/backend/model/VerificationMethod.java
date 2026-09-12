package dev.ngb.backend.model;

/**
 * How a piece of provider evidence was proved authentic.
 *
 * <p>A browser return, a deep link, an SDK callback, or a guest's word can prompt a status refresh
 * but never appears here, because none of them proves anything about money.</p>
 */
public enum VerificationMethod {
    /** The provider's signature over the exact received body verified. */
    WEBHOOK_SIGNATURE,
    /** Retrieved over an authenticated server-to-server call. */
    AUTHENTICATED_API,
    /** Taken from a provider file through a restricted, audited path. */
    RESTRICTED_IMPORT
}
