package dev.ngb.backend.identity.internal.service.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.identity.internal.model.account.AccountHolderType;
import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import dev.ngb.backend.identity.internal.model.account.ContactChannelType;
import dev.ngb.backend.identity.internal.model.credential.AuthCredential;
import dev.ngb.backend.identity.internal.model.credential.CredentialType;
import dev.ngb.backend.identity.internal.model.session.AuthToken;
import dev.ngb.backend.identity.internal.model.session.TokenConsumptionReason;
import dev.ngb.backend.identity.internal.model.account.User;
import dev.ngb.backend.identity.internal.model.account.UserStatus;
import dev.ngb.backend.identity.internal.repository.account.AccountHolderRepository;
import dev.ngb.backend.identity.internal.repository.account.ContactChannelRepository;
import dev.ngb.backend.identity.internal.repository.credential.AuthCredentialRepository;
import dev.ngb.backend.identity.internal.repository.session.AuthTokenRepository;
import dev.ngb.backend.identity.internal.repository.account.UserRepository;
import dev.ngb.backend.identity.internal.repository.capability.UserRoleRepository;
import dev.ngb.backend.identity.internal.service.auth.passwordreset.PasswordCredentialRotator;
import dev.ngb.backend.identity.internal.service.auth.session.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ngb.backend.identity.internal.exception.InvalidCredentialsException;
import dev.ngb.backend.identity.internal.exception.UserNotFoundException;
import dev.ngb.backend.identity.internal.service.user.UserFinder;
import dev.ngb.backend.identity.internal.service.validation.PasswordPolicy;
import dev.ngb.backend.identity.internal.web.ChangePasswordRequest;
import dev.ngb.backend.identity.internal.web.UserResponse;
import dev.ngb.backend.platform.exception.base.ValidationException;
import dev.ngb.backend.platform.util.StringUtils;


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
    private final AccountHolderRepository accountHolderRepository;
    private final ContactChannelRepository contactChannelRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordCredentialRotator passwordCredentialRotator;
    private final RefreshTokenService refreshTokenService;
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
        ContactChannel emailChannel = contactChannelRepository
                .findCurrentPrimary(userId, ContactChannelType.EMAIL.name())
                .orElse(null);
        UUID accountHolderId = accountHolderRepository
                .findByUserIdAndHolderType(userId, AccountHolderType.PERSON)
                .map(AccountHolder::getId)
                .orElse(null);
        return UserResponse.from(
                user, emailChannel, accountHolderId, userRoleRepository.findRolesByUserId(userId));
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
     * Verifies the old password and enrolls a newly encoded, different password.
     *
     * @param userId authenticated account identifier
     * @param request current and replacement raw passwords
     * @throws InvalidCredentialsException when the current password is wrong
     * @throws ValidationException when the replacement violates policy or matches the old password
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        passwordPolicy.validate("newPassword", request.newPassword());

        userFinder.findActiveById(userId);
        AuthCredential currentCredential = authCredentialRepository
                .findActive(userId, CredentialType.PASSWORD.name())
                .orElseThrow(InvalidCredentialsException::new);
        validatePasswordChange(
                request.currentPassword(), request.newPassword(), currentCredential.getVerifierDigest());

        PasswordCredentialRotator.Rotation rotation = passwordCredentialRotator.rotate(
                currentCredential, request.newPassword(), clock.instant());
        authCredentialRepository.save(rotation.disabledOld());
        authCredentialRepository.save(rotation.enrolledNew());
    }

    /**
     * Soft-deletes the authenticated account, closes its account holder, and revokes every
     * outstanding session and opaque token.
     *
     * @param userId authenticated account identifier
     */
    @Transactional
    public void deleteOwnAccount(UUID userId) {
        User user = userFinder.findActiveByIdForUpdate(userId);
        applyStatus(user, UserStatus.DELETED);
    }

    private void validatePasswordChange(
            String currentPassword,
            String newPassword,
            String verifierDigest) {
        if (!passwordEncoder.matches(currentPassword, verifierDigest)) {
            throw new InvalidCredentialsException();
        }
        if (passwordEncoder.matches(newPassword, verifierDigest)) {
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

        if (status == UserStatus.DELETED) {
            accountHolderRepository.findByUserIdAndHolderType(user.getId(), AccountHolderType.PERSON)
                    .filter(holder -> holder.getStatus() != AccountHolderStatus.CLOSED)
                    .ifPresent(holder -> {
                        holder.setStatus(AccountHolderStatus.CLOSED);
                        accountHolderRepository.save(holder);
                    });
        }
        if (status != UserStatus.ACTIVE) {
            refreshTokenService.revokeAllSessionsForUser(user.getId(), now, "ACCOUNT_DELETED");
            revokeOutstandingTokens(user.getId(), now);
        }
    }

    private void revokeOutstandingTokens(UUID userId, Instant now) {
        List<AuthToken> tokens = authTokenRepository.findAllByUserIdAndConsumedAtIsNull(userId);
        tokens.forEach(token -> token.consume(now, TokenConsumptionReason.REVOKED));
        authTokenRepository.saveAll(tokens);
    }

}
