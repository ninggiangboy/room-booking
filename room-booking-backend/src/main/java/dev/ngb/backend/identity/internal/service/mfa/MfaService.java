package dev.ngb.backend.identity.internal.service.mfa;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ngb.backend.identity.internal.exception.InvalidStepUpProofException;
import dev.ngb.backend.identity.internal.exception.InvalidTotpCodeException;
import dev.ngb.backend.identity.internal.exception.NoActiveTotpCredentialException;
import dev.ngb.backend.identity.internal.exception.TotpAlreadyEnrolledException;
import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import dev.ngb.backend.identity.internal.model.account.ContactChannelType;
import dev.ngb.backend.identity.internal.model.credential.AuthCredential;
import dev.ngb.backend.identity.internal.model.credential.CredentialType;
import dev.ngb.backend.identity.internal.model.session.AuthToken;
import dev.ngb.backend.identity.internal.model.session.AuthTokenType;
import dev.ngb.backend.identity.internal.model.session.TokenConsumptionReason;
import dev.ngb.backend.identity.internal.repository.account.ContactChannelRepository;
import dev.ngb.backend.identity.internal.repository.credential.AuthCredentialRepository;
import dev.ngb.backend.identity.internal.repository.session.AuthTokenRepository;
import dev.ngb.backend.identity.internal.service.account.AccountHolderFinder;
import dev.ngb.backend.identity.internal.service.auth.AuthTokenFactory;
import dev.ngb.backend.platform.SecretBox;
import dev.ngb.backend.platform.util.HashUtils;
import dev.ngb.backend.platform.util.TotpUtils;

/**
 * Enrolls TOTP as a second factor and issues the short-lived proof a sensitive action can demand
 * before proceeding.
 *
 * <p>{@code @Service} identifies business logic; Lombok generates constructor injection for the
 * final dependencies. Public entry points are transactional so credential enrollment, disabling,
 * and step-up proof issuance and consumption each commit or roll back as one unit.</p>
 */
@Service
@RequiredArgsConstructor
public class MfaService {

    private static final String ISSUER = "Room Booking";
    private static final int TOTP_DIGITS = 6;
    private static final Duration TOTP_STEP = Duration.ofSeconds(30);
    private static final int TOTP_WINDOW_STEPS = 1;

    private final AccountHolderFinder accountHolderFinder;
    private final AuthCredentialRepository authCredentialRepository;
    private final ContactChannelRepository contactChannelRepository;
    private final TotpCredentialFactory totpCredentialFactory;
    private final SecretBox secretBox;
    private final AuthTokenRepository authTokenRepository;
    private final AuthTokenFactory authTokenFactory;

    @Value("${app.mfa.step-up-token-ttl:5m}")
    private Duration stepUpTokenTtl;

    /** Ensures invalid TTL configuration fails during application startup. */
    @PostConstruct
    void validateConfiguration() {
        if (stepUpTokenTtl.isNegative() || stepUpTokenTtl.isZero()) {
            throw new IllegalStateException("step-up token TTL must be positive");
        }
    }

    /**
     * Enrolls a new TOTP factor, returning the seed and provisioning URI exactly once.
     *
     * <p>The factor is active immediately (the schema has no "pending" credential state — see
     * {@code docs/implementation/identity/09-roadmap.md}), but it cannot successfully complete a
     * step-up until the holder proves they can produce a code from it, which is what makes a
     * mistyped or never-scanned enrollment self-correcting rather than a silent lockout: nothing in
     * this codebase requires TOTP to log in, only to step up.</p>
     *
     * @param holderId account enrolling the factor
     * @param decisionInstant the command's single decision instant
     * @return the Base32 seed and provisioning URI to show the holder; neither is recoverable again
     * @throws TotpAlreadyEnrolledException when the holder already has an active TOTP credential
     */
    @Transactional
    public EnrolledTotp enrollTotp(UUID holderId, Instant decisionInstant) {
        accountHolderFinder.findActiveById(holderId);
        if (authCredentialRepository.countByAccountHolderIdAndCredentialTypeAndDisabledAtIsNull(
                holderId, CredentialType.TOTP) > 0) {
            throw new TotpAlreadyEnrolledException(holderId);
        }

        TotpCredentialFactory.EnrolledTotp built = totpCredentialFactory.create(holderId, decisionInstant);
        authCredentialRepository.save(built.credential());

        String accountLabel = contactChannelRepository
                .findCurrentPrimary(holderId, ContactChannelType.EMAIL.name())
                .map(ContactChannel::getNormalizedValue)
                .orElse(holderId.toString());
        return new EnrolledTotp(
                TotpUtils.base32Encode(built.rawSecret()),
                TotpUtils.buildProvisioningUri(ISSUER, accountLabel, built.rawSecret()));
    }

    /**
     * Disables the holder's active TOTP factor.
     *
     * @param holderId account disabling the factor
     * @param decisionInstant the command's single decision instant
     * @throws NoActiveTotpCredentialException when no active TOTP credential is enrolled
     */
    @Transactional
    public void disableTotp(UUID holderId, Instant decisionInstant) {
        AuthCredential credential = authCredentialRepository
                .findActive(holderId, CredentialType.TOTP.name())
                .orElseThrow(() -> new NoActiveTotpCredentialException(holderId));
        credential.disable(decisionInstant);
        authCredentialRepository.save(credential);
    }

    /**
     * Verifies a TOTP code and, on success, issues a short-lived opaque step-up proof.
     *
     * <p>The proof is a {@link AuthTokenType#STEP_UP} row on the same one-time-secret mechanism
     * every other opaque credential in this module uses: only its digest is persisted, and it is
     * consumed exactly once by {@link #consumeStepUpProof}.</p>
     *
     * @param holderId account proving the factor
     * @param rawCode code as the holder typed it
     * @param decisionInstant the command's single decision instant
     * @return the raw proof to present to the sensitive action being stepped up to
     * @throws NoActiveTotpCredentialException when no active TOTP credential is enrolled
     * @throws InvalidTotpCodeException when the code does not match within the accepted window
     */
    @Transactional
    public String stepUp(UUID holderId, String rawCode, Instant decisionInstant) {
        AuthCredential credential = authCredentialRepository
                .findActive(holderId, CredentialType.TOTP.name())
                .orElseThrow(() -> new NoActiveTotpCredentialException(holderId));

        byte[] secret = secretBox.open(credential.getSecretReference(), credential.getKeyVersion());
        if (!TotpUtils.verifyCode(
                secret, rawCode, decisionInstant, TOTP_DIGITS, TOTP_STEP, TOTP_WINDOW_STEPS)) {
            throw new InvalidTotpCodeException();
        }
        credential.setLastUsedAt(decisionInstant);
        authCredentialRepository.save(credential);

        AuthTokenFactory.IssuedToken issued = authTokenFactory.create(
                holderId, AuthTokenType.STEP_UP, decisionInstant, stepUpTokenTtl);
        authTokenRepository.save(issued.token());
        return issued.rawToken();
    }

    /**
     * Consumes a step-up proof, the primitive a sensitive action calls before proceeding.
     *
     * <p>No caller in this codebase invokes this yet — see
     * {@code docs/implementation/identity/09-roadmap.md} for which workflow is the natural first
     * one to wire it into.</p>
     *
     * @param holderId account the proof must belong to
     * @param rawProof proof previously returned by {@link #stepUp}
     * @param decisionInstant the command's single decision instant
     * @throws InvalidStepUpProofException when the proof is unknown, expired, consumed, or belongs
     *     to a different account holder
     */
    @Transactional
    public void consumeStepUpProof(UUID holderId, String rawProof, Instant decisionInstant) {
        AuthToken token = authTokenRepository
                .findByTokenHashAndType(HashUtils.sha256Hex(rawProof), AuthTokenType.STEP_UP)
                .filter(candidate -> holderId.equals(candidate.getAccountHolderId()))
                .orElseThrow(InvalidStepUpProofException::new);
        if (!token.isUsableAt(decisionInstant)) {
            throw new InvalidStepUpProofException();
        }
        token.consume(decisionInstant, TokenConsumptionReason.USED);
        authTokenRepository.save(token);
    }

    /**
     * The seed and provisioning URI a caller shows the holder exactly once at enrollment.
     *
     * @param base32Secret seed encoded for manual entry into an authenticator app
     * @param provisioningUri {@code otpauth://totp/} URI the app can scan as a QR code
     */
    public record EnrolledTotp(String base32Secret, String provisioningUri) {
    }
}
