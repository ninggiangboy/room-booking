package dev.ngb.backend.service.host;

import dev.ngb.backend.dto.HostOnboardingRequest;
import dev.ngb.backend.dto.HostOnboardingResponse;
import dev.ngb.backend.dto.HostProfileResponse;
import dev.ngb.backend.dto.UserResponse;
import dev.ngb.backend.exception.UserNotFoundException;
import dev.ngb.backend.model.HostProfile;
import dev.ngb.backend.model.IdentityStatus;
import dev.ngb.backend.model.Role;
import dev.ngb.backend.model.User;
import dev.ngb.backend.repository.HostProfileRepository;
import dev.ngb.backend.repository.UserRoleRepository;
import dev.ngb.backend.service.user.UserFinder;
import dev.ngb.backend.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Owns the single workflow that promotes an active guest account to a host account. */
@Service
@RequiredArgsConstructor
public class HostOnboardingService {

    private final UserRoleRepository userRoleRepository;
    private final HostProfileRepository hostProfileRepository;
    private final UserFinder userFinder;
    private final Clock clock;

    /**
     * Creates the caller's host profile and grants {@link Role#HOST} in one transaction.
     * Repeating the request returns the existing profile and repairs a missing role assignment.
     *
     * @param userId authenticated account identifier
     * @param request optional public profile information
     * @return account roles and host profile after onboarding
     * @throws dev.ngb.backend.exception.UserAccountDisabledException when the account is inactive
     * @throws UserNotFoundException when the account does not exist
     */
    @Transactional
    public HostOnboardingResponse onboard(UUID userId, HostOnboardingRequest request) {
        User user = userFinder.findActiveByIdForUpdate(userId);

        Instant now = clock.instant();
        HostProfile profile = hostProfileRepository.findById(userId).orElseGet(() ->
                hostProfileRepository.save(HostProfile.builder()
                        .userId(userId)
                        .bio(normalizeOptional(request.bio()))
                        .identityStatus(IdentityStatus.UNVERIFIED)
                        .build()));

        userRoleRepository.grantRole(userId, Role.HOST.name(), now);
        List<Role> roles = userRoleRepository.findRolesByUserId(userId);
        return new HostOnboardingResponse(
                UserResponse.from(user, roles),
                HostProfileResponse.from(profile));
    }

    private static @Nullable String normalizeOptional(@Nullable String value) {
        String normalized = StringUtils.normalize(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }
}
