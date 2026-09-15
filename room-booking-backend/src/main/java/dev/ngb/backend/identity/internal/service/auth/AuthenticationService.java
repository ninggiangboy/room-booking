package dev.ngb.backend.identity.internal.service.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import dev.ngb.backend.identity.internal.model.account.ContactChannelType;
import dev.ngb.backend.identity.internal.model.capability.GrantSource;
import dev.ngb.backend.identity.internal.model.capability.PrincipalType;
import dev.ngb.backend.identity.internal.model.credential.AuthCredential;
import dev.ngb.backend.identity.internal.model.credential.CredentialType;
import dev.ngb.backend.identity.internal.model.session.AuthenticationMethod;
import dev.ngb.backend.identity.internal.repository.account.AccountHolderRepository;
import dev.ngb.backend.identity.internal.repository.account.ContactChannelRepository;
import dev.ngb.backend.identity.internal.repository.credential.AuthCredentialRepository;
import dev.ngb.backend.identity.internal.service.account.AccountHolderFinder;
import dev.ngb.backend.identity.internal.service.auth.session.RefreshTokenService;
import dev.ngb.backend.identity.internal.service.authz.AuthorizationService;
import dev.ngb.backend.identity.internal.service.authz.CapabilityGrantService;
import dev.ngb.backend.identity.internal.service.authz.RoleBundle;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ngb.backend.identity.AccessTokenService;
import dev.ngb.backend.identity.internal.exception.EmailAlreadyRegisteredException;
import dev.ngb.backend.identity.internal.exception.InvalidCredentialsException;
import dev.ngb.backend.identity.internal.exception.InvalidRefreshTokenException;
import dev.ngb.backend.identity.internal.exception.UserAccountDisabledException;
import dev.ngb.backend.identity.internal.service.validation.PasswordPolicy;
import dev.ngb.backend.identity.internal.web.AuthResponse;
import dev.ngb.backend.identity.internal.web.LoginRequest;
import dev.ngb.backend.identity.internal.web.LogoutRequest;
import dev.ngb.backend.identity.internal.web.RefreshTokenRequest;
import dev.ngb.backend.identity.internal.web.RegisterRequest;
import dev.ngb.backend.identity.internal.web.UserResponse;
import dev.ngb.backend.platform.AssuranceLevel;
import dev.ngb.backend.platform.util.StringUtils;


/**
 * Coordinates account registration, credential login, token refresh, and logout.
 *
 * <p>{@code @Service} registers this class as application/business logic. Lombok's
 * {@code @RequiredArgsConstructor} generates a constructor containing every final dependency;
 * Spring injects their beans without mutable field injection.</p>
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final String REGISTRATION_REASON = "SELF_SERVICE_REGISTRATION";

    private final AccountHolderRepository accountHolderRepository;
    private final ContactChannelRepository contactChannelRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordPolicy passwordPolicy;
    private final AccountHolderFinder accountHolderFinder;
    private final UserRegistrationFactory userRegistrationFactory;
    private final CapabilityGrantService capabilityGrantService;
    private final AuthorizationService authorizationService;
    private final Clock clock;

    /**
     * Validates credentials and issues a fresh access/refresh token pair.
     *
     * <p>{@code @Transactional} keeps database reads and refresh-token creation in one unit of
     * work. The same generic credentials error is used for an unknown email and a wrong password
     * to avoid revealing registered addresses.</p>
     *
     * @param request validated login DTO from the controller
     * @return new authenticated-session response
     * @throws InvalidCredentialsException when the email or password does not match
     * @throws UserAccountDisabledException when the account is not active
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = StringUtils.normalizeLowerCase(request.email());

        AccountHolder holder = accountHolderFinder.findActiveByEmail(
                normalizedEmail, InvalidCredentialsException::new);

        AuthCredential credential = authCredentialRepository
                .findActive(holder.getId(), CredentialType.PASSWORD.name())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), credential.getVerifierDigest())) {
            throw new InvalidCredentialsException();
        }

        RefreshTokenService.Issued issued = refreshTokenService.issue(
                holder.getId(), AuthenticationMethod.PASSWORD, AssuranceLevel.AAL1);
        String accessToken = accessTokenService.generateAccessToken(
                holder.getId(), accessTokenClaims(issued.sessionId()));
        return new AuthResponse(accessToken, issued.rawRefreshToken(), buildUserResponse(holder));
    }

    /**
     * Consumes a refresh token and rotates it into a new token pair.
     *
     * @param request DTO containing the raw refresh token
     * @return replacement access token, refresh token, and user projection
     * @throws InvalidRefreshTokenException when the token is unusable, or reuse is detected
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshTokenService.Rotated rotated = refreshTokenService.consume(request.refreshToken());
        AccountHolder holder = accountHolderFinder.findActiveById(
                rotated.accountHolderId(), InvalidCredentialsException::new);
        String accessToken = accessTokenService.generateAccessToken(
                holder.getId(), accessTokenClaims(rotated.sessionId()));
        return new AuthResponse(accessToken, rotated.rawRefreshToken(), buildUserResponse(holder));
    }

    /**
     * Revokes a refresh token's session if it exists and has not already been revoked.
     *
     * @param request DTO identifying the session token to revoke
     */
    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    /**
     * Creates a validated guest account and initial authenticated session.
     *
     * <p>All inserts share one transaction. The early existence check produces a fast readable
     * error; unlike the retired {@code users.email} column, {@code contact_channels} carries no
     * database-level uniqueness for an unverified claim (two accounts may each claim the same
     * address before either proves it), so this check is a best-effort guard against an accidental
     * duplicate rather than the final defense against a deliberate race.</p>
     *
     * @param request Bean-validated registration data
     * @return initial token pair and newly created account
     * @throws EmailAlreadyRegisteredException when the normalized email is already claimed
     */
    @Transactional
    public AuthResponse registerUser(RegisterRequest request) {
        String normalizedEmail = StringUtils.normalizeLowerCase(request.email());
        String normalizedDisplayName = StringUtils.normalizeRequired(request.displayName());
        passwordPolicy.validate(request.password());

        if (contactChannelRepository.existsByChannelTypeAndNormalizedValue(
                ContactChannelType.EMAIL, normalizedEmail)) {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }

        Instant issuedAt = clock.instant();
        UserRegistrationFactory.NewAccount newAccount = userRegistrationFactory.create(
                request.email(),
                normalizedEmail,
                request.password(),
                normalizedDisplayName,
                issuedAt);

        AccountHolder holder;
        ContactChannel emailChannel;
        try {
            holder = accountHolderRepository.save(newAccount.accountHolder());
            emailChannel = contactChannelRepository.save(newAccount.emailChannel());
        } catch (DataIntegrityViolationException exception) {
            // A concurrent request that also passed the check above and won the same race fails
            // here instead: account_holders itself carries no email, so contact_channels is where
            // any remaining database-level protection would surface.
            throw new EmailAlreadyRegisteredException(normalizedEmail, exception);
        }
        authCredentialRepository.save(newAccount.passwordCredential());

        // Reuse the holder's audited createdAt as the grant instant so every row from this
        // registration shares one decision instant instead of a second, later clock read.
        capabilityGrantService.issueRoleGrant(
                PrincipalType.PERSON,
                holder.getId(),
                RoleBundle.GUEST,
                GrantSource.SELF_SERVICE,
                REGISTRATION_REASON,
                holder.getCreatedAt());

        RefreshTokenService.Issued issued = refreshTokenService.issue(
                holder.getId(), AuthenticationMethod.PASSWORD, AssuranceLevel.AAL1);
        String accessToken = accessTokenService.generateAccessToken(
                holder.getId(), accessTokenClaims(issued.sessionId()));
        UserResponse userResponse = UserResponse.from(
                holder,
                emailChannel,
                null,
                List.of(RoleBundle.GUEST.name()),
                roleBundleCapabilityNames(RoleBundle.GUEST));
        return new AuthResponse(accessToken, issued.rawRefreshToken(), userResponse);
    }

    private Map<String, ?> accessTokenClaims(UUID sessionId) {
        return Map.of("sid", sessionId.toString());
    }

    private UserResponse buildUserResponse(AccountHolder holder) {
        Instant now = clock.instant();
        ContactChannel emailChannel = contactChannelRepository
                .findCurrentPrimary(holder.getId(), ContactChannelType.EMAIL.name())
                .orElse(null);
        List<String> roleNames = capabilityGrantService.effectiveRoleNames(holder.getId(), now);
        Set<String> capabilities = authorizationService.effectiveGlobalCapabilities(holder.getId(), now)
                .stream()
                .map(Enum::name)
                .collect(Collectors.toSet());
        return UserResponse.from(holder, emailChannel, null, roleNames, capabilities);
    }

    private static Set<String> roleBundleCapabilityNames(RoleBundle role) {
        return role.capabilities().stream().map(Enum::name).collect(Collectors.toSet());
    }

}
