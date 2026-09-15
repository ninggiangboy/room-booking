package dev.ngb.backend.identity.internal.service.auth.passwordreset;

import java.time.Instant;
import java.util.UUID;
import dev.ngb.backend.identity.internal.model.credential.AuthCredential;
import dev.ngb.backend.identity.internal.model.credential.CredentialType;
import dev.ngb.backend.identity.internal.service.auth.AuthTokenFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;


/**
 * Builds the disabled-old and enrolled-new credential rows a password change must persist
 * together.
 *
 * <p>{@code @Component} makes the factory injectable. Lombok generates constructor injection for
 * the encoder. Unlike {@code UserRegistrationFactory} and {@link AuthTokenFactory}, this factory
 * is {@code public} rather than package-private: password rotation is needed by both
 * {@code service.auth.passwordreset} ({@link PasswordResetService}) and {@code service.account}
 * ({@code UserAccountService}), and rotating a credential exposes no raw secret that
 * package-private visibility would need to protect.</p>
 *
 * <p>A credential is replaced rather than edited in place, consistent with {@link AuthCredential}
 * being "disabled rather than deleted" so an investigation can still see that a factor was once
 * present.</p>
 */
@Component
@RequiredArgsConstructor
public class PasswordCredentialRotator {

    private final PasswordEncoder passwordEncoder;

    /**
     * Disables the current password credential and builds its active replacement.
     *
     * <p>The caller must already have verified that {@code newRawPassword} differs from the
     * password {@code current} verifies, since this factory encodes but never compares
     * passwords.</p>
     *
     * @param current active password credential being replaced
     * @param newRawPassword replacement password to encode, never stored in its original form
     * @param instant the command's decision instant
     * @return the now-disabled old row and the unsaved new row to persist together
     */
    public Rotation rotate(AuthCredential current, String newRawPassword, Instant instant) {
        current.disable(instant);

        AuthCredential next = AuthCredential.builder()
                .id(UUID.randomUUID())
                .accountHolderId(current.getAccountHolderId())
                .credentialType(CredentialType.PASSWORD)
                .encoderId("bcrypt")
                .verifierDigest(passwordEncoder.encode(newRawPassword))
                .enrolledAt(instant)
                .build();

        return new Rotation(current, next);
    }

    /**
     * The disabled and enrolled credential rows a password change must persist together.
     *
     * @param disabledOld the credential that stopped being usable
     * @param enrolledNew the credential that replaces it
     */
    public record Rotation(AuthCredential disabledOld, AuthCredential enrolledNew) {
    }
}
