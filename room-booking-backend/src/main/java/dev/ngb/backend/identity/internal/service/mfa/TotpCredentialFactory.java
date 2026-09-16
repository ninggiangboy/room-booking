package dev.ngb.backend.identity.internal.service.mfa;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

import dev.ngb.backend.identity.internal.model.credential.AuthCredential;
import dev.ngb.backend.identity.internal.model.credential.CredentialType;
import dev.ngb.backend.platform.SecretBox;
import dev.ngb.backend.platform.util.TotpUtils;

/**
 * Constructs a new TOTP credential row from a freshly generated seed.
 *
 * <p>{@code @Component} makes the factory injectable; Lombok is not used here since the single
 * {@link SecretBox} collaborator is wired through an explicit constructor to keep the raw seed's
 * lifetime visible in one place: generated, sealed, and handed to the caller as a Base32 string —
 * never logged, never stored anywhere but inside the sealed {@link AuthCredential#getSecretReference()}
 * this factory returns. Package-private, matching every other factory in this codebase.</p>
 */
@Component
class TotpCredentialFactory {

    private final SecretBox secretBox;

    TotpCredentialFactory(SecretBox secretBox) {
        this.secretBox = secretBox;
    }

    /**
     * Generates a new seed, seals it, and builds the unsaved credential row.
     *
     * @param accountHolderId account the credential authenticates
     * @param issuedAt enrollment command's decision instant
     * @return the unsaved credential and the raw seed to show the holder exactly once
     */
    EnrolledTotp create(UUID accountHolderId, Instant issuedAt) {
        byte[] rawSecret = TotpUtils.generateSecret();
        SecretBox.SealedSecret sealed = secretBox.seal(rawSecret);

        AuthCredential credential = AuthCredential.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .credentialType(CredentialType.TOTP)
                .encoderId("totp-sha1")
                .secretReference(sealed.reference())
                .keyVersion(sealed.keyVersion())
                .enrolledAt(issuedAt)
                .build();

        return new EnrolledTotp(credential, rawSecret);
    }

    /**
     * The unsaved credential row and the raw seed a caller must show the holder exactly once.
     *
     * @param credential unsaved credential row holding only the sealed seed
     * @param rawSecret raw seed to encode for the holder's authenticator app
     */
    record EnrolledTotp(AuthCredential credential, byte[] rawSecret) {
    }
}
