package dev.ngb.backend.model;

/**
 * How the principal proved who they were when a session was established.
 *
 * <p>Recorded on the session because a later command may demand a stronger proof than the one that
 * opened it, which is what step-up authentication means.</p>
 */
public enum AuthenticationMethod {
    /** Password only. */
    PASSWORD,
    /** Password plus a second factor. */
    PASSWORD_MFA,
    /** An external identity provider. */
    FEDERATED,
    /** An account-recovery flow. */
    RECOVERY,
    /** Established before authentication methods were recorded. */
    LEGACY
}
