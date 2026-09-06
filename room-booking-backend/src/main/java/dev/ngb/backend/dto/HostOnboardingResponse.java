package dev.ngb.backend.dto;

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
