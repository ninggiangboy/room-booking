package dev.ngb.backend.identity.internal.service.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import dev.ngb.backend.identity.internal.model.account.ContactChannelType;
import dev.ngb.backend.identity.internal.model.credential.AuthCredential;
import dev.ngb.backend.identity.internal.model.credential.CredentialType;
import dev.ngb.backend.identity.internal.model.session.AuthToken;
import dev.ngb.backend.identity.internal.model.session.TokenConsumptionReason;
import dev.ngb.backend.identity.internal.repository.account.AccountHolderRepository;
import dev.ngb.backend.identity.internal.repository.account.ContactChannelRepository;
import dev.ngb.backend.identity.internal.repository.credential.AuthCredentialRepository;
import dev.ngb.backend.identity.internal.repository.session.AuthTokenRepository;
import dev.ngb.backend.identity.internal.service.auth.passwordreset.PasswordCredentialRotator;
import dev.ngb.backend.identity.internal.service.auth.session.RefreshTokenService;
import dev.ngb.backend.identity.internal.service.authz.AuthorizationService;
import dev.ngb.backend.identity.internal.service.authz.CapabilityGrantService;
import dev.ngb.backend.identity.internal.service.validation.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ngb.backend.identity.internal.exception.InvalidCredentialsException;
import dev.ngb.backend.identity.internal.exception.UserNotFoundException;
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

    private final AccountHolderRepository accountHolderRepository;
    private final ContactChannelRepository contactChannelRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordCredentialRotator passwordCredentialRotator;
    private final RefreshTokenService refreshTokenService;
    private final CapabilityGrantService capabilityGrantService;
    private final AuthorizationService authorizationService;
    private final AccountHolderFinder accountHolderFinder;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final Clock clock;

    /**
     * Loads the holder and grant projection returned by the current-user endpoint.
     *
     * <p>{@code readOnly = true} documents that this transaction performs no persistence update.</p>
     *
     * @param holderId authenticated account identifier
     * @return safe user response with separately loaded roles and capabilities
     * @throws UserNotFoundException when the identifier no longer exists
     */
    @Transactional(readOnly = true)
    public UserResponse getUser(UUID holderId) {
        AccountHolder holder = accountHolderFinder.findById(holderId);
        ContactChannel emailChannel = contactChannelRepository
                .findCurrentPrimary(holderId, ContactChannelType.EMAIL.name())
                .orElse(null);
        Instant now = clock.instant();
        List<String> roleNames = capabilityGrantService.effectiveRoleNames(holderId, now);
        Set<String> capabilities = authorizationService.effectiveGlobalCapabilities(holderId, now)
                .stream()
                .map(Enum::name)
                .collect(Collectors.toSet());
        return UserResponse.from(holder, emailChannel, null, roleNames, capabilities);
    }

    /**
     * Normalizes an email before checking whether it is already claimed.
     *
     * @param email possibly untrimmed, mixed-case query value
     * @return {@code true} when the normalized address is claimed by any channel, verified or not
     */
    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        String normalizedEmail = StringUtils.normalizeLowerCase(email);
        return contactChannelRepository.existsByChannelTypeAndNormalizedValue(
                ContactChannelType.EMAIL, normalizedEmail);
    }

    /**
     * Verifies the old password and enrolls a newly encoded, different password.
     *
     * @param holderId authenticated account identifier
     * @param request current and replacement raw passwords
     * @throws InvalidCredentialsException when the current password is wrong
     * @throws ValidationException when the replacement violates policy or matches the old password
     */
    @Transactional
    public void changePassword(UUID holderId, ChangePasswordRequest request) {
        passwordPolicy.validate("newPassword", request.newPassword());

        accountHolderFinder.findActiveById(holderId);
        AuthCredential currentCredential = authCredentialRepository
                .findActive(holderId, CredentialType.PASSWORD.name())
                .orElseThrow(InvalidCredentialsException::new);
        validatePasswordChange(
                request.currentPassword(), request.newPassword(), currentCredential.getVerifierDigest());

        PasswordCredentialRotator.Rotation rotation = passwordCredentialRotator.rotate(
                currentCredential, request.newPassword(), clock.instant());
        authCredentialRepository.save(rotation.disabledOld());
        authCredentialRepository.save(rotation.enrolledNew());
    }

    /**
     * Closes the authenticated account and revokes every outstanding session and opaque token.
     *
     * @param holderId authenticated account identifier
     */
    @Transactional
    public void deleteOwnAccount(UUID holderId) {
        AccountHolder holder = accountHolderFinder.findActiveByIdForUpdate(holderId);
        applyStatus(holder, AccountHolderStatus.CLOSED);
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

    private void applyStatus(AccountHolder holder, AccountHolderStatus status) {
        if (holder.getStatus() == status) {
            return;
        }
        Instant now = clock.instant();
        holder.setStatus(status);
        accountHolderRepository.save(holder);

        if (status != AccountHolderStatus.ACTIVE) {
            refreshTokenService.revokeAllSessionsForHolder(holder.getId(), now, "ACCOUNT_CLOSED");
            revokeOutstandingTokens(holder.getId(), now);
        }
    }

    private void revokeOutstandingTokens(UUID holderId, Instant now) {
        List<AuthToken> tokens = authTokenRepository.findAllByAccountHolderIdAndConsumedAtIsNull(holderId);
        tokens.forEach(token -> token.consume(now, TokenConsumptionReason.REVOKED));
        authTokenRepository.saveAll(tokens);
    }

}
