package dev.ngb.backend.service.host;

import java.util.UUID;

import dev.ngb.backend.model.HostProfile;
import dev.ngb.backend.model.IdentityStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Constructs the host profile created when a guest account is promoted to a host account.
 *
 * <p>{@code @Component} makes the factory injectable, and package-private visibility keeps profile
 * construction inside the onboarding package. Holding the initial identity state here means a new
 * profile can never start in a reviewed state by accident.</p>
 */
@Component
class HostProfileFactory {

    /**
     * Builds an unverified host profile keyed by the owning account.
     *
     * @param userId account being promoted; also the profile's primary key
     * @param bio normalized public biography, or {@code null} when the host supplied none
     * @return unsaved host profile awaiting identity review
     */
    HostProfile create(UUID userId, @Nullable String bio) {
        return HostProfile.builder()
                .userId(userId)
                .bio(bio)
                .identityStatus(IdentityStatus.UNVERIFIED)
                .build();
    }
}
