package dev.ngb.backend.model;

/**
 * Kind of authentication material enrolled for a principal.
 *
 * <p>At most one of each type may be active per principal: a second active password would make
 * "which one is current" a question the login path has to guess at.</p>
 */
public enum CredentialType {
    /** A password, stored only as a verifier digest. */
    PASSWORD,
    /** A time-based one-time-password seed, held through the secret boundary. */
    TOTP,
    /** A WebAuthn authenticator. */
    WEBAUTHN,
    /** A single-use account-recovery code. */
    RECOVERY_CODE,
    /** An external identity-provider linkage. */
    FEDERATED
}
