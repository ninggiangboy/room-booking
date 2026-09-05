package dev.ngb.backend.service.account;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import dev.ngb.backend.dto.UserResponse;
import dev.ngb.backend.dto.ChangePasswordRequest;
import dev.ngb.backend.exception.InvalidCredentialsException;
import dev.ngb.backend.exception.UserNotFoundException;
import dev.ngb.backend.exception.ValidationException;
import dev.ngb.backend.model.User;
import dev.ngb.backend.repository.UserRepository;
import dev.ngb.backend.repository.UserRoleRepository;
import dev.ngb.backend.service.user.UserFinder;
import dev.ngb.backend.service.validation.PasswordPolicy;
import dev.ngb.backend.service.validation.UserAccountPolicy;
import dev.ngb.backend.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates authenticated profile reads and account changes inside database transactions.
 *
 * <p>{@code @Service} identifies business logic. Lombok generates constructor injection for every
 * final dependency, and method-level {@code @Transactional} annotations define database units of
 * work.</p>
 */
@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserFinder userFinder;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final UserAccountPolicy userAccountPolicy;
    private final Clock clock;

    /**
     * Loads the user and role projection returned by the current-user endpoint.
     *
     * <p>{@code readOnly = true} documents that this transaction performs no persistence update.</p>
     *
     * @param userId authenticated account identifier
     * @return safe user response with separately loaded roles
     * @throws UserNotFoundException when the identifier no longer exists
     */
    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        User user = userFinder.findById(userId);
        return UserResponse.from(user, userRoleRepository.findRolesByUserId(userId));
    }

    /**
     * Normalizes an email before checking whether it is already registered.
     *
     * @param email possibly untrimmed, mixed-case query value
     * @return {@code true} when the normalized address exists
     * @throws ValidationException when the value is null or blank
     */
    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        String normalizedEmail = StringUtils.normalizeLowerCase(email);
        ValidationException.requireNonBlank("email", normalizedEmail);
        return userRepository.existsByEmail(normalizedEmail);
    }

    /**
     * Verifies the old password and persists a newly encoded, different password.
     *
     * @param userId authenticated account identifier
     * @param request current and replacement raw passwords
     * @throws InvalidCredentialsException when the current password is wrong
     * @throws ValidationException when the replacement violates policy or matches the old password
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        Objects.requireNonNull(userId, "userId must not be null");
        passwordPolicy.validate("newPassword", request.newPassword());

        User user = userFinder.findById(userId);
        userAccountPolicy.requireActive(user);
        validatePasswordChange(request.currentPassword(), request.newPassword(), user.getPasswordHash());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(clock.instant());
        userRepository.save(user);
    }

    private void validatePasswordChange(
            String currentPassword,
            String newPassword,
            String passwordHash) {
        if (!passwordEncoder.matches(currentPassword, passwordHash)) {
            throw new InvalidCredentialsException();
        }
        if (passwordEncoder.matches(newPassword, passwordHash)) {
            throw new ValidationException(
                    "newPassword",
                    "new password must be different from current password");
        }
    }

}
