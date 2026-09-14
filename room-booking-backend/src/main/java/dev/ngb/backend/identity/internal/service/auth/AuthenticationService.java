package dev.ngb.backend.identity.internal.service.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import dev.ngb.backend.identity.internal.model.Role;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderType;
import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import dev.ngb.backend.identity.internal.model.account.ContactChannelType;
import dev.ngb.backend.identity.internal.model.account.User;
import dev.ngb.backend.identity.internal.model.credential.AuthCredential;
import dev.ngb.backend.identity.internal.model.credential.CredentialType;
import dev.ngb.backend.identity.internal.model.session.AuthenticationMethod;
import dev.ngb.backend.identity.internal.repository.account.AccountHolderRepository;
import dev.ngb.backend.identity.internal.repository.account.ContactChannelRepository;
import dev.ngb.backend.identity.internal.repository.account.UserRepository;
import dev.ngb.backend.identity.internal.repository.capability.UserRoleRepository;
import dev.ngb.backend.identity.internal.repository.credential.AuthCredentialRepository;
import dev.ngb.backend.identity.internal.service.auth.session.RefreshTokenService;
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
import dev.ngb.backend.identity.internal.service.user.UserFinder;
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

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final ContactChannelRepository contactChannelRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordPolicy passwordPolicy;
    private final UserFinder userFinder;
    private final UserRegistrationFactory userRegistrationFactory;
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

        User user = userFinder.findActiveByEmail(
                normalizedEmail, InvalidCredentialsException::new);

        AuthCredential credential = authCredentialRepository
                .findActive(user.getId(), CredentialType.PASSWORD.name())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), credential.getVerifierDigest())) {
            throw new InvalidCredentialsException();
        }

        return createAuthResponse(
                user, userRoleRepository.findRolesByUserId(user.getId()),
                AuthenticationMethod.PASSWORD, AssuranceLevel.AAL1);
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
        if (rotated.rawRefreshToken() == null) {
            // The presented token predates sessions and cannot be rotated into a session-backed
            // successor; the caller must log in again to obtain one.
            throw new InvalidRefreshTokenException();
        }

        User user = userFinder.findActiveById(rotated.userId(), InvalidCredentialsException::new);
        List<Role> roles = userRoleRepository.findRolesByUserId(user.getId());
        String accessToken = accessTokenService.generateAccessToken(
                user.getId(), accessTokenClaims(user, roles));
        return new AuthResponse(
                accessToken, rotated.rawRefreshToken(), buildUserResponse(user, roles));
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
     * error, while the database unique constraint remains the final defense against concurrent
     * requests registering the same address.</p>
     *
     * @param request Bean-validated registration data
     * @return initial token pair and newly created user
     * @throws EmailAlreadyRegisteredException when the normalized email is already present
     */
    @Transactional
    public AuthResponse registerUser(RegisterRequest request) {
        String normalizedEmail = StringUtils.normalizeLowerCase(request.email());
        String normalizedDisplayName = StringUtils.normalizeRequired(request.displayName());
        passwordPolicy.validate(request.password());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }

        Instant issuedAt = clock.instant();
        UserRegistrationFactory.NewAccount newAccount = userRegistrationFactory.create(
                request.email(),
                normalizedEmail,
                request.password(),
                normalizedDisplayName,
                issuedAt);
        User user = newAccount.user();

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            // The database unique constraint closes the race between the earlier check and this insert.
            throw new EmailAlreadyRegisteredException(normalizedEmail, exception);
        }

        accountHolderRepository.save(newAccount.accountHolder());
        ContactChannel emailChannel = contactChannelRepository.save(newAccount.emailChannel());
        authCredentialRepository.save(newAccount.passwordCredential());

        // Reuse the user's audited createdAt as the role's grant instant so both rows share
        // registration's single decision instant instead of a second, later clock read.
        userRoleRepository.grantRole(
                user.getId(),
                newAccount.initialRole().getId().getRole().name(),
                user.getCreatedAt());

        List<Role> roles = List.of(Role.GUEST);
        String accessToken = accessTokenService.generateAccessToken(
                user.getId(), accessTokenClaims(user, roles));
        String refreshToken = refreshTokenService.issue(
                user, AuthenticationMethod.PASSWORD, AssuranceLevel.AAL1);
        UserResponse userResponse = UserResponse.from(
                user, emailChannel, newAccount.accountHolder().getId(), roles);
        return new AuthResponse(accessToken, refreshToken, userResponse);
    }

    private AuthResponse createAuthResponse(
            User user, List<Role> roles, AuthenticationMethod method, AssuranceLevel assuranceLevel) {
        String accessToken = accessTokenService.generateAccessToken(
                user.getId(), accessTokenClaims(user, roles));
        // Refresh tokens are opaque, session-backed, and stored only as hashes.
        String refreshToken = refreshTokenService.issue(user, method, assuranceLevel);
        return new AuthResponse(accessToken, refreshToken, buildUserResponse(user, roles));
    }

    private Map<String, ?> accessTokenClaims(User user, List<Role> roles) {
        List<String> roleNames = roles.stream().map(Role::name).toList();
        return Map.of("email", user.getEmail(), "roles", roleNames);
    }

    private UserResponse buildUserResponse(User user, List<Role> roles) {
        ContactChannel emailChannel = contactChannelRepository
                .findCurrentPrimary(user.getId(), ContactChannelType.EMAIL.name())
                .orElse(null);
        UUID accountHolderId = accountHolderRepository
                .findByUserIdAndHolderType(user.getId(), AccountHolderType.PERSON)
                .map(AccountHolder::getId)
                .orElse(null);
        return UserResponse.from(user, emailChannel, accountHolderId, roles);
    }

}
