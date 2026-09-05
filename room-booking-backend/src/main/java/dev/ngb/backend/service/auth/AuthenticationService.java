package dev.ngb.backend.service.auth;

import java.util.List;
import java.util.Map;

import dev.ngb.backend.dto.AuthResponse;
import dev.ngb.backend.dto.LoginRequest;
import dev.ngb.backend.dto.LogoutRequest;
import dev.ngb.backend.dto.RefreshTokenRequest;
import dev.ngb.backend.dto.RegisterRequest;
import dev.ngb.backend.dto.UserResponse;
import dev.ngb.backend.exception.EmailAlreadyRegisteredException;
import dev.ngb.backend.exception.InvalidCredentialsException;
import dev.ngb.backend.exception.UserAccountDisabledException;
import dev.ngb.backend.model.Role;
import dev.ngb.backend.model.User;
import dev.ngb.backend.repository.UserRepository;
import dev.ngb.backend.repository.UserRoleRepository;
import dev.ngb.backend.service.validation.PasswordPolicy;
import dev.ngb.backend.service.validation.UserAccountPolicy;
import dev.ngb.backend.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordPolicy passwordPolicy;
    private final UserAccountPolicy userAccountPolicy;
    private final UserRegistrationFactory userRegistrationFactory;

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

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        userAccountPolicy.requireActive(user);

        return createAuthResponse(user, userRoleRepository.findRolesByUserId(user.getId()));
    }

    /**
     * Consumes a one-time refresh token and rotates it into a new token pair.
     *
     * @param request DTO containing the raw refresh token
     * @return replacement access token, refresh token, and user projection
     * @throws dev.ngb.backend.exception.InvalidRefreshTokenException when the token is unusable
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        var userId = refreshTokenService.consume(request.refreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        userAccountPolicy.requireActive(user);
        return createAuthResponse(user, userRoleRepository.findRolesByUserId(userId));
    }

    /**
     * Revokes a refresh token if it exists and has not already been consumed.
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
        String normalizedDisplayName = StringUtils.normalize(request.displayName());
        passwordPolicy.validate(request.password());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }

        UserRegistrationFactory.NewUser newUser = userRegistrationFactory.create(
                normalizedEmail,
                request.password(),
                normalizedDisplayName);
        User user = newUser.user();

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            // The database unique constraint closes the race between the earlier check and this insert.
            throw new EmailAlreadyRegisteredException(normalizedEmail, exception);
        }

        userRoleRepository.save(newUser.initialRole());
        return createAuthResponse(user, List.of(Role.GUEST));
    }

    private AuthResponse createAuthResponse(User user, List<Role> roles) {
        // Access tokens are signed JWTs; refresh tokens are opaque and stored only as hashes.
        List<String> roleNames = roles.stream().map(Role::name).toList();
        Map<String, ?> claims = Map.of("email", user.getEmail(), "roles", roleNames);
        String accessToken = accessTokenService.generateAccessToken(user.getId(), claims);
        String refreshToken = refreshTokenService.issue(user);
        return new AuthResponse(accessToken, refreshToken, UserResponse.from(user, roles));
    }

}
