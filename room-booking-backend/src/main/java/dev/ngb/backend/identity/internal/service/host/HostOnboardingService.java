package dev.ngb.backend.identity.internal.service.host;

import dev.ngb.backend.identity.internal.model.capability.HostProfile;
import dev.ngb.backend.identity.internal.model.Role;
import dev.ngb.backend.identity.internal.model.account.User;
import dev.ngb.backend.identity.internal.model.capability.GrantSource;
import dev.ngb.backend.identity.internal.model.capability.PrincipalType;
import dev.ngb.backend.identity.internal.repository.capability.HostProfileRepository;
import dev.ngb.backend.identity.internal.repository.capability.UserRoleRepository;
import dev.ngb.backend.identity.internal.service.authz.CapabilityGrantService;
import dev.ngb.backend.identity.internal.service.authz.RoleBundle;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.identity.internal.exception.UserNotFoundException;
import dev.ngb.backend.identity.internal.service.user.UserFinder;
import dev.ngb.backend.identity.internal.web.HostProfileResponse;
import dev.ngb.backend.identity.internal.web.UserResponse;
import dev.ngb.backend.platform.util.StringUtils;


/** Owns the single workflow that promotes an active guest account to a host account. */
@Service
@RequiredArgsConstructor
public class HostOnboardingService {

    private static final String HOST_ONBOARDING_REASON = "SELF_SERVICE_HOST_ONBOARDING";

    private final UserRoleRepository userRoleRepository;
    private final HostProfileRepository hostProfileRepository;
    private final HostProfileFactory hostProfileFactory;
    private final CapabilityGrantService capabilityGrantService;
    private final UserFinder userFinder;
    private final Clock clock;

    /**
     * Creates the caller's host profile and grants the host role in one transaction. Repeating
     * the request returns the existing profile and repairs a missing role or capability grant.
     *
     * <p>Both {@link Role#HOST} and its {@link RoleBundle#HOST} capability grant are issued here.
     * Authorization decisions already flow through the capability grant; the role row remains the
     * compatibility surface until migration 037 retires {@code user_roles}, which is what keeps
     * this change revertible without a database migration.</p>
     *
     * @param userId authenticated account identifier
     * @param request optional public profile information
     * @return account roles and host profile after onboarding
     * @throws dev.ngb.backend.identity.internal.exception.UserAccountDisabledException when the account is inactive
     * @throws UserNotFoundException when the account does not exist
     */
    @Transactional
    public HostOnboardingResponse onboard(UUID userId, HostOnboardingRequest request) {
        User user = userFinder.findActiveByIdForUpdate(userId);

        Instant now = clock.instant();
        HostProfile profile = hostProfileRepository.findById(userId).orElseGet(() ->
                hostProfileRepository.save(
                        hostProfileFactory.create(userId, normalizeOptional(request.bio()))));

        userRoleRepository.grantRole(userId, Role.HOST.name(), now);
        capabilityGrantService.findEffectiveRoleGrant(userId, RoleBundle.HOST, now)
                .orElseGet(() -> capabilityGrantService.issueRoleGrant(
                        PrincipalType.USER,
                        userId,
                        RoleBundle.HOST,
                        GrantSource.SELF_SERVICE,
                        HOST_ONBOARDING_REASON,
                        now));

        List<Role> roles = userRoleRepository.findRolesByUserId(userId);
        return new HostOnboardingResponse(
                UserResponse.from(user, null, roles),
                HostProfileResponse.from(profile));
    }

    private static @Nullable String normalizeOptional(@Nullable String value) {
        String normalized = StringUtils.normalize(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }
}
