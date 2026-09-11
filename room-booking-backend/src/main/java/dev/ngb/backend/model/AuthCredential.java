package dev.ngb.backend.model;

import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One piece of authentication material enrolled for a principal.
 *
 * <p>Credentials come in two shapes and the row must be exactly one of them. Material we only ever
 * verify — a password — is kept as a {@link #verifierDigest} and can never be read back. Material we
 * must recover to use — a TOTP seed — is kept as a {@link #secretReference} through the secret
 * boundary, with the key version that protects it. A row with both, or with neither, could not
 * authenticate anyone, and the database refuses it.</p>
 *
 * <p>Enrolments are disabled rather than deleted, so an investigation can still see that a factor
 * was once present.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("auth_credentials")
public class AuthCredential {

    /** Primary key of the credential. */
    @Id
    private @Nullable UUID id;
    /** Principal the credential authenticates. */
    private UUID userId;
    /** Kind of authentication material. */
    private CredentialType credentialType;
    /** Encoder or algorithm identifier, so a rehash can target outdated entries. */
    private String encoderId;
    /** Verifier digest for material that is only ever checked, never recovered. */
    private @Nullable String verifierDigest;
    /** Secret-manager reference for material that must be read back to be used. */
    private @Nullable String secretReference;
    /** Version of the key protecting {@link #secretReference}. */
    private @Nullable Short keyVersion;
    /** UTC instant the credential was enrolled. */
    private Instant enrolledAt;
    /** UTC instant it was last used successfully. */
    private @Nullable Instant lastUsedAt;
    /** UTC instant it stopped being usable; {@code null} while active. */
    private @Nullable Instant disabledAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;

    /**
     * Reports whether this credential may still be used to authenticate.
     *
     * @return {@code true} when the credential has not been disabled
     */
    public boolean isActive() {
        return disabledAt == null;
    }
}
