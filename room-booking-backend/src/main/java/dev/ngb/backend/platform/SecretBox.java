package dev.ngb.backend.platform;

/**
 * Port used by application code to seal material that must be read back later — a TOTP seed, for
 * instance — without embedding an encryption scheme or key-management detail in the caller.
 *
 * <p>An interface defines behavior without implementation, matching {@link EmailSender} and
 * {@link SmsSender}: a module depends on this abstraction, while Spring injects the concrete
 * implementation. Unlike a digest, sealing is reversible by design — {@code
 * AuthCredential}'s class documentation calls this "the secret boundary": material the application
 * must recover to use is kept as a reference through this boundary rather than as one more column
 * the application reads directly.</p>
 */
public interface SecretBox {

    /**
     * Seals plaintext material so it can later be recovered only through {@link #open}.
     *
     * @param plaintext material to seal; never logged or persisted by the caller in this form
     * @return an opaque reference and the key version that protects it
     */
    SealedSecret seal(byte[] plaintext);

    /**
     * Recovers material previously sealed by {@link #seal}.
     *
     * @param reference opaque reference returned by {@link #seal}
     * @param keyVersion key version returned alongside that reference
     * @return the original plaintext
     */
    byte[] open(String reference, short keyVersion);

    /**
     * An opaque reference to sealed material, and the key version that protects it.
     *
     * <p>Mirrors {@link dev.ngb.backend.identity.internal.model.credential.AuthCredential}'s
     * {@code secretReference}/{@code keyVersion} column pair exactly, so a caller can persist both
     * fields without any further translation.</p>
     *
     * @param reference opaque reference to store as {@code secret_reference}
     * @param keyVersion key version to store as {@code key_version}
     */
    record SealedSecret(String reference, short keyVersion) {
    }
}
