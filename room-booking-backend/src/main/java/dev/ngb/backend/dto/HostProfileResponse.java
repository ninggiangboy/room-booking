package dev.ngb.backend.dto;

import dev.ngb.backend.model.HostProfile;
import dev.ngb.backend.model.IdentityStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Public projection of the host-specific profile created during onboarding.
 *
 * @param userId owning account identifier
 * @param bio optional public host biography
 * @param identityStatus identity-review state
 * @param averageRating cached published-review average, or {@code null}
 * @param reviewCount number of reviews included in the average
 * @param createdAt profile creation instant
 * @param updatedAt most recent profile update instant
 */
public record HostProfileResponse(
        UUID userId,
        @Nullable String bio,
        IdentityStatus identityStatus,
        @Nullable BigDecimal averageRating,
        int reviewCount,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Creates a safe response from a persistence entity.
     *
     * @param profile persisted host profile
     * @return immutable API projection
     */
    public static HostProfileResponse from(HostProfile profile) {
        return new HostProfileResponse(
                profile.getUserId(),
                profile.getBio(),
                profile.getIdentityStatus(),
                profile.getAverageRating(),
                profile.getReviewCount(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
