package dev.ngb.backend.identity.internal.service.auth.passwordreset;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.identity.internal.model.account.ContactChannelType;
import dev.ngb.backend.identity.internal.model.credential.AuthCredential;
import dev.ngb.backend.identity.internal.model.credential.CredentialType;
import dev.ngb.backend.identity.internal.model.session.AuthToken;
import dev.ngb.backend.identity.internal.model.session.AuthTokenType;
import dev.ngb.backend.identity.internal.model.session.TokenConsumptionReason;
import dev.ngb.backend.identity.internal.repository.account.AccountHolderRepository;
import dev.ngb.backend.identity.internal.repository.account.ContactChannelRepository;
import dev.ngb.backend.identity.internal.repository.credential.AuthCredentialRepository;
import dev.ngb.backend.identity.internal.repository.session.AuthTokenRepository;
import dev.ngb.backend.identity.internal.service.account.AccountHolderFinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ngb.backend.identity.PasswordResetIssued;
import dev.ngb.backend.identity.internal.exception.InvalidPasswordResetTokenException;
import dev.ngb.backend.identity.internal.service.auth.AuthTokenFactory;
import dev.ngb.backend.identity.internal.service.auth.session.RefreshTokenService;
import dev.ngb.backend.identity.internal.service.validation.PasswordPolicy;
import dev.ngb.backend.identity.internal.web.ForgotPasswordRequest;
import dev.ngb.backend.identity.internal.web.ResetPasswordRequest;
import dev.ngb.backend.platform.exception.base.ValidationException;
import dev.ngb.backend.platform.util.DurationUtils;
import dev.ngb.backend.platform.util.HashUtils;
import dev.ngb.backend.platform.util.StringUtils;


/**
 * Issues and consumes one-time password-reset tokens without exposing registered emails.
 *
 * <p>{@code @Service} registers the workflow. An explicit constructor validates the
 * {@code @Value}-injected TTL and makes every dependency mandatory. Public methods use
 * {@code @Transactional} so token/credential/session changes either commit together or roll back
 * together.</p>
 */
@Service
public class PasswordResetService {

    private static final String EMAIL_VERIFICATION_METHOD = "PASSWORD_RESET_POSSESSION";

    private final AuthTokenRepository authTokenRepository;
    private final AuthTokenFactory authTokenFactory;
    private final AccountHolderRepository accountHolderRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final ContactChannelRepository contactChannelRepository;
    private final PasswordCredentialRotator passwordCredentialRotator;
    private final RefreshTokenService refreshTokenService;
    private final AccountHolderFinder accountHolderFinder;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final Duration tokenTtl;

    /**
     * Creates a password-reset workflow with a validated token lifetime.
     *
     * @param authTokenRepository persistence gateway for reset tokens
     * @param authTokenFactory builder of token records and their raw secrets
     * @param accountHolderRepository persistence gateway for account holders
     * @param authCredentialRepository persistence gateway for password credentials
     * @param contactChannelRepository persistence gateway for contact channels
     * @param passwordCredentialRotator builder of the disabled-old/enrolled-new credential pair
     * @param refreshTokenService revokes outstanding sessions after a credential recovery
     * @param accountHolderFinder shared account-holder lookup and active-status gateway
     * @param passwordEncoder verifies and hashes passwords
     * @param passwordPolicy enforces password strength and BCrypt limits
     * @param eventPublisher publishes a raw token for post-commit email delivery
     * @param clock shared testable source of current time
     * @param tokenTtl configured positive password-reset-token lifetime
     */
    public PasswordResetService(
            AuthTokenRepository authTokenRepository,
            AuthTokenFactory authTokenFactory,
            AccountHolderRepository accountHolderRepository,
            AuthCredentialRepository authCredentialRepository,
            ContactChannelRepository contactChannelRepository,
            PasswordCredentialRotator passwordCredentialRotator,
            RefreshTokenService refreshTokenService,
            AccountHolderFinder accountHolderFinder,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            @Value("${app.password-reset.token-ttl:30m}") Duration tokenTtl) {
        this.authTokenRepository = authTokenRepository;
        this.authTokenFactory = authTokenFactory;
        this.accountHolderRepository = accountHolderRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.contactChannelRepository = contactChannelRepository;
        this.passwordCredentialRotator = passwordCredentialRotator;
        this.refreshTokenService = refreshTokenService;
        this.accountHolderFinder = accountHolderFinder;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.tokenTtl = DurationUtils.requirePositive(tokenTtl, "password reset token TTL");
    }

    /**
     * Sends a reset link when an active account exists. The method deliberately succeeds for all
     * syntactically valid emails so callers cannot enumerate accounts from its response.
     *
     * <p>Issuing a new token consumes any older unconsumed password-reset tokens for the account.</p>
     *
     * @param request validated email request; email is normalized before lookup
     */
    @Transactional
    public void requestReset(ForgotPasswordRequest request) {
        String normalizedEmail = StringUtils.normalizeLowerCase(request.email());
        accountHolderFinder.findActiveByEmailIfPresent(normalizedEmail).ifPresent(this::issue);
    }

    /**
     * Replaces the password, confirms control of the email address, consumes the reset token, and
     * revokes every live session.
     *
     * <p>Revoking sessions signs the user out of every existing renewable session after a
     * credential recovery. Existing short-lived JWT access tokens naturally expire.</p>
     *
     * @param request one-time reset token and proposed new raw password
     * @throws InvalidPasswordResetTokenException when token or owning account is unusable
     * @throws ValidationException when the password violates policy or repeats the current one
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        passwordPolicy.validate("newPassword", request.newPassword());

        Instant now = clock.instant();
        AuthToken resetToken = authTokenRepository.findByTokenHashAndType(
                        HashUtils.sha256Hex(request.token()), AuthTokenType.PASSWORD_RESET)
                .orElseThrow(InvalidPasswordResetTokenException::new);
        if (!resetToken.isUsableAt(now)) {
            throw new InvalidPasswordResetTokenException();
        }

        AccountHolder holder = accountHolderRepository.findById(resetToken.getAccountHolderId())
                .filter(candidate -> candidate.getStatus() == AccountHolderStatus.ACTIVE
                        || candidate.getStatus() == AccountHolderStatus.PENDING_VERIFICATION)
                .orElseThrow(InvalidPasswordResetTokenException::new);
        AuthCredential currentCredential = authCredentialRepository
                .findActive(holder.getId(), CredentialType.PASSWORD.name())
                .orElseThrow(InvalidPasswordResetTokenException::new);
        if (passwordEncoder.matches(request.newPassword(), currentCredential.getVerifierDigest())) {
            throw new ValidationException(
                    "newPassword", "new password must be different from current password");
        }

        resetToken.consume(now, TokenConsumptionReason.USED);
        authTokenRepository.save(resetToken);

        PasswordCredentialRotator.Rotation rotation = passwordCredentialRotator.rotate(
                currentCredential, request.newPassword(), now);
        authCredentialRepository.save(rotation.disabledOld());
        authCredentialRepository.save(rotation.enrolledNew());

        // Possessing the reset link proves control of the email address, the same way an explicit
        // verification token does.
        contactChannelRepository
                .findCurrentPrimary(holder.getId(), ContactChannelType.EMAIL.name())
                .filter(channel -> !channel.isVerified())
                .ifPresent(channel -> {
                    channel.markVerified(now, EMAIL_VERIFICATION_METHOD);
                    contactChannelRepository.save(channel);
                });

        refreshTokenService.revokeAllSessionsForHolder(holder.getId(), now, "PASSWORD_RESET");
    }

    private void issue(AccountHolder holder) {
        Instant now = clock.instant();
        authTokenRepository.findAllByAccountHolderIdAndTypeAndConsumedAtIsNull(
                        holder.getId(), AuthTokenType.PASSWORD_RESET)
                .forEach(token -> {
                    token.consume(now, TokenConsumptionReason.ROTATED);
                    authTokenRepository.save(token);
                });

        AuthTokenFactory.IssuedToken issued = authTokenFactory.create(
                holder.getId(), AuthTokenType.PASSWORD_RESET, now, tokenTtl);
        authTokenRepository.save(issued.token());
        String email = contactChannelRepository
                .findCurrentPrimary(holder.getId(), ContactChannelType.EMAIL.name())
                .map(channel -> channel.getNormalizedValue())
                .orElseThrow(() -> new IllegalStateException(
                        "registration must create a primary email channel"));
        eventPublisher.publishEvent(new PasswordResetIssued(email, issued.rawToken()));
    }

}
