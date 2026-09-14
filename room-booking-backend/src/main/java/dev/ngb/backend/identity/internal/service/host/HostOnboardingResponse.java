package dev.ngb.backend.identity.internal.service.host;
import dev.ngb.backend.identity.internal.web.HostProfileResponse;
import dev.ngb.backend.identity.internal.web.UserResponse;

/**
 * Result of atomically creating a host profile and granting the host role.
 *
 * @param user updated account projection containing the {@code HOST} role
 * @param hostProfile newly created or existing host profile
 */
public record HostOnboardingResponse(
        UserResponse user,
        HostProfileResponse hostProfile) {
}
