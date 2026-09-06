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
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC aggregate representing a registered account in the {@code users} table.
 *
 * <p>Lombok's {@code @Getter}/{@code @Setter} generate accessors, {@code @Builder} generates a
 * fluent object builder, and the constructor annotations generate the forms needed by Spring Data
 * and application code. {@code @Table} selects the database table, {@code @Id} marks the primary
 * key, and {@code @Version} enables optimistic locking.</p>
 *
 * <p>{@code @NoArgsConstructor(access = PROTECTED)} allows the persistence framework to construct
 * an empty object without advertising that incomplete state as normal application usage.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("users")
public class User {

    /** Primary key; application registration currently supplies a random UUID. */
    @Id
    private UUID id;
    /** Normalized case-insensitive login address. */
    private String email;
    /** Optional phone number reserved for phone identity features. */
    private @Nullable String phoneNumber;
    /** One-way encoded password; raw passwords must never be assigned here. */
    private String passwordHash;
    /** Public name shown to other users. */
    private String displayName;
    /** Optional location of the user's avatar. */
    private @Nullable String avatarUrl;
    /** Soft lifecycle state; deleted accounts remain for historical references. */
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;
    /** UTC instant of successful email verification, or {@code null}. */
    private @Nullable Instant emailVerifiedAt;
    /** UTC instant of successful phone verification, or {@code null}. */
    private @Nullable Instant phoneVerifiedAt;
    /** Creation time maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** Last modification time maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Version compared during updates to detect concurrent writes. */
    @Version
    private @Nullable Long version;

    /**
     * Returns whether business operations may currently be performed by this account.
     *
     * @return {@code true} only for {@link UserStatus#ACTIVE}
     */
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
