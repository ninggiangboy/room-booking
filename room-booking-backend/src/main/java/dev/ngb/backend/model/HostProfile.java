package dev.ngb.backend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Optional host-specific information whose primary key is also the owning user's identifier.
 *
 * <p>Lombok supplies entity accessors, constructors, and builder methods. Spring Data JDBC maps
 * this aggregate to {@code host_profiles}; its {@code @Id} is both profile ID and user ID.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_profiles")
public class HostProfile {

    /** Shared primary key and foreign key to the owning user. */
    @Id
    private UUID userId;
    /** Optional host biography. */
    private String bio;
    /** Current identity-review workflow state. */
    @Builder.Default
    private IdentityStatus identityStatus = IdentityStatus.UNVERIFIED;
    /** Cached published-review average, represented exactly as a decimal. */
    private BigDecimal averageRating;
    /** Number of reviews included in {@link #averageRating}. */
    @Builder.Default
    private int reviewCount = 0;
    /** UTC creation time. */
    @Builder.Default
    private Instant createdAt = Instant.now();
    /** UTC time of the most recent application update. */
    @Builder.Default
    private Instant updatedAt = Instant.now();
    /** Optimistic-lock version used to detect concurrent profile edits. */
    @Version
    private Long version;
}
