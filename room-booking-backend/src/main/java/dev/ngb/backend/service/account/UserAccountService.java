package dev.ngb.backend.service.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import dev.ngb.backend.dto.UserResponse;
import dev.ngb.backend.dto.ChangePasswordRequest;
import dev.ngb.backend.dto.UpdateProfileRequest;
import dev.ngb.backend.exception.InvalidCredentialsException;
import dev.ngb.backend.exception.PhoneNumberAlreadyUsedException;
import dev.ngb.backend.exception.UserNotFoundException;
import dev.ngb.backend.exception.base.ValidationException;
import dev.ngb.backend.model.User;
import dev.ngb.backend.model.AuthToken;
import dev.ngb.backend.model.UserStatus;
import dev.ngb.backend.repository.AuthTokenRepository;
import dev.ngb.backend.repository.UserRepository;
import dev.ngb.backend.repository.UserRoleRepository;
import dev.ngb.backend.service.user.UserFinder;
import dev.ngb.backend.service.validation.PasswordPolicy;
import dev.ngb.backend.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final AuthTokenRepository authTokenRepository;
    private final UserFinder userFinder;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
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
        User user = userFinder.findById(userId);
        return UserResponse.from(user, userRoleRepository.findRolesByUserId(userId));
    }

    /**
     * Normalizes an email before checking whether it is already registered.
     *
     * @param email possibly untrimmed, mixed-case query value
     * @return {@code true} when the normalized address exists
     */
    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        String normalizedEmail = StringUtils.normalizeLowerCase(email);
        return userRepository.existsByEmail(normalizedEmail);
    }

    /**
     * Applies a partial update to the authenticated user's public profile.
     *
     * <p>Absent ({@code null}) components leave the stored value untouched, so clients may send
     * only the fields they change. A blank {@code avatarUrl} or {@code phoneNumber} clears the
     * nullable column, while a blank {@code displayName} is rejected because every account must
     * keep a public name.</p>
     *
     * @param userId authenticated account identifier
     * @param request partial profile values
     * @return refreshed user projection including roles
     * @throws UserNotFoundException when the identifier no longer exists
     * @throws dev.ngb.backend.exception.UserAccountDisabledException when the account is inactive
     * @throws ValidationException when a supplied display name is blank
     * @throws PhoneNumberAlreadyUsedException when another account already owns the phone number
     */
    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userFinder.findActiveById(userId);

        if (request.displayName() != null) {
            user.setDisplayName(requireDisplayName(request.displayName()));
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(StringUtils.normalizeOptional(request.avatarUrl()));
        }
        String phoneNumber = StringUtils.normalizeOptional(request.phoneNumber());
        if (request.phoneNumber() != null
                && !Objects.equals(phoneNumber, user.getPhoneNumber())) {
            // A replacement number is unverified, so the previous verification no longer applies.
            user.setPhoneNumber(phoneNumber);
            user.setPhoneVerifiedAt(null);
        }

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            // The unique constraint on phone_number is the only conflict this update can raise.
            throw new PhoneNumberAlreadyUsedException(
                    Objects.toString(phoneNumber, ""), exception);
        }
        return UserResponse.from(user, userRoleRepository.findRolesByUserId(userId));
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
        passwordPolicy.validate("newPassword", request.newPassword());

        User user = userFinder.findActiveById(userId);
        validatePasswordChange(request.currentPassword(), request.newPassword(), user.getPasswordHash());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    /**
     * Soft-deletes the authenticated account and revokes all outstanding opaque tokens.
     *
     * @param userId authenticated account identifier
     */
    @Transactional
    public void deleteOwnAccount(UUID userId) {
        User user = userFinder.findActiveByIdForUpdate(userId);
        applyStatus(user, UserStatus.DELETED);
    }

    private static String requireDisplayName(String displayName) {
        String normalized = StringUtils.normalizeRequired(displayName);
        if (normalized.isEmpty()) {
            throw new ValidationException("displayName", "displayName must not be blank");
        }
        return normalized;
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

    private void applyStatus(User user, UserStatus status) {
        if (user.getStatus() == status) {
            return;
        }
        Instant now = clock.instant();
        user.setStatus(status);
        userRepository.save(user);
        if (status != UserStatus.ACTIVE) {
            revokeOutstandingTokens(user.getId(), now);
        }
    }

    private void revokeOutstandingTokens(UUID userId, Instant now) {
        List<AuthToken> tokens = authTokenRepository.findAllByUserIdAndConsumedAtIsNull(userId);
        tokens.forEach(token -> token.setConsumedAt(now));
        authTokenRepository.saveAll(tokens);
    }

}
