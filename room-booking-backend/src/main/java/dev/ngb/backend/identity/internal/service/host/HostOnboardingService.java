package dev.ngb.backend.identity.internal.service.host;

import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import dev.ngb.backend.identity.internal.model.account.ContactChannelType;
import dev.ngb.backend.identity.internal.model.capability.GrantSource;
import dev.ngb.backend.identity.internal.model.capability.PrincipalType;
import dev.ngb.backend.identity.internal.repository.account.ContactChannelRepository;
import dev.ngb.backend.identity.internal.service.account.AccountHolderFinder;
import dev.ngb.backend.identity.internal.service.authz.AuthorizationService;
import dev.ngb.backend.identity.internal.service.authz.CapabilityGrantService;
import dev.ngb.backend.identity.internal.service.authz.RoleBundle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.ngb.backend.identity.internal.exception.UserNotFoundException;
import dev.ngb.backend.identity.internal.web.UserResponse;


/**
 * Owns the single workflow that promotes an active guest account to a host account.
 *
 * <p>{@code @Service} registers this as business logic; Lombok's {@code @RequiredArgsConstructor}
 * generates constructor injection for every final dependency. {@code host_profiles} is gone as of
 * migration {@code 037}: {@code bio}, {@code average_rating}, and {@code review_count} were never
 * this module's facts to own (see {@code docs/features/identity-accounts-and-access.md} §
 * Profile facts and their consumers), so onboarding now does exactly one thing -- issue the
 * {@code HOST} capability grant -- and returns nothing but the account projection.</p>
 */
@Service
@RequiredArgsConstructor
public class HostOnboardingService {

    private static final String HOST_ONBOARDING_REASON = "SELF_SERVICE_HOST_ONBOARDING";

    private final CapabilityGrantService capabilityGrantService;
    private final AuthorizationService authorizationService;
    private final AccountHolderFinder accountHolderFinder;
    private final ContactChannelRepository contactChannelRepository;
    private final Clock clock;

    /**
     * Grants the caller the host role, idempotently, in one transaction.
     *
     * <p>Repeating the request finds the existing effective grant through {@link
     * CapabilityGrantService#findEffectiveRoleGrant} and returns the current projection rather
     * than issuing a duplicate grant.</p>
     *
     * @param holderId authenticated account identifier
     * @return account projection reflecting the host role and its capabilities
     * @throws dev.ngb.backend.identity.internal.exception.UserAccountDisabledException when the account is inactive
     * @throws UserNotFoundException when the account does not exist
     */
    @Transactional
    public UserResponse onboard(UUID holderId) {
        AccountHolder holder = accountHolderFinder.findActiveByIdForUpdate(holderId);

        Instant now = clock.instant();
        capabilityGrantService.findEffectiveRoleGrant(holderId, RoleBundle.HOST, now)
                .orElseGet(() -> capabilityGrantService.issueRoleGrant(
                        PrincipalType.PERSON,
                        holderId,
                        RoleBundle.HOST,
                        GrantSource.SELF_SERVICE,
                        HOST_ONBOARDING_REASON,
                        now));

        ContactChannel emailChannel = contactChannelRepository
                .findCurrentPrimary(holderId, ContactChannelType.EMAIL.name())
                .orElse(null);
        List<String> roleNames = capabilityGrantService.effectiveRoleNames(holderId, now);
        Set<String> capabilities = authorizationService.effectiveGlobalCapabilities(holderId, now)
                .stream()
                .map(Enum::name)
                .collect(Collectors.toSet());
        return UserResponse.from(holder, emailChannel, null, roleNames, capabilities);
    }
}
