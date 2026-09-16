package dev.ngb.backend.identity.internal.service.mfa;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import dev.ngb.backend.identity.internal.exception.InvalidStepUpProofException;
import dev.ngb.backend.identity.internal.exception.InvalidTotpCodeException;
import dev.ngb.backend.identity.internal.exception.NoActiveTotpCredentialException;
import dev.ngb.backend.identity.internal.exception.TotpAlreadyEnrolledException;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.identity.internal.model.credential.AuthCredential;
import dev.ngb.backend.identity.internal.model.credential.CredentialType;
import dev.ngb.backend.identity.internal.model.session.AuthToken;
import dev.ngb.backend.identity.internal.model.session.AuthTokenType;
import dev.ngb.backend.identity.internal.repository.account.ContactChannelRepository;
import dev.ngb.backend.identity.internal.repository.credential.AuthCredentialRepository;
import dev.ngb.backend.identity.internal.repository.session.AuthTokenRepository;
import dev.ngb.backend.identity.internal.service.account.AccountHolderFinder;
import dev.ngb.backend.identity.internal.service.auth.AuthTokenFactory;
import dev.ngb.backend.platform.SecretBox;
import dev.ngb.backend.platform.util.HashUtils;
import dev.ngb.backend.platform.util.TotpUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private AccountHolderFinder accountHolderFinder;
    @Mock
    private AuthCredentialRepository authCredentialRepository;
    @Mock
    private ContactChannelRepository contactChannelRepository;
    @Mock
    private TotpCredentialFactory totpCredentialFactory;
    @Mock
    private SecretBox secretBox;
    @Mock
    private AuthTokenRepository authTokenRepository;
    @Mock
    private AuthTokenFactory authTokenFactory;

    private MfaService service;
    private UUID holderId;

    @BeforeEach
    void setUp() {
        service = new MfaService(
                accountHolderFinder, authCredentialRepository, contactChannelRepository,
                totpCredentialFactory, secretBox, authTokenRepository, authTokenFactory);
        ReflectionTestUtils.setField(service, "stepUpTokenTtl", Duration.ofMinutes(5));
        holderId = UUID.randomUUID();
    }

    @Test
    void enrollingWhenAlreadyEnrolledFails() {
        when(accountHolderFinder.findActiveById(holderId)).thenReturn(activeHolder());
        when(authCredentialRepository.countByAccountHolderIdAndCredentialTypeAndDisabledAtIsNull(
                holderId, CredentialType.TOTP)).thenReturn(1L);

        assertThatThrownBy(() -> service.enrollTotp(holderId, NOW))
                .isInstanceOf(TotpAlreadyEnrolledException.class);
        verify(authCredentialRepository, never()).save(any());
    }

    @Test
    void enrollingSavesTheCredentialAndReturnsTheSeedOnce() {
        when(accountHolderFinder.findActiveById(holderId)).thenReturn(activeHolder());
        when(authCredentialRepository.countByAccountHolderIdAndCredentialTypeAndDisabledAtIsNull(
                holderId, CredentialType.TOTP)).thenReturn(0L);
        byte[] rawSecret = TotpUtils.generateSecret();
        AuthCredential built = AuthCredential.builder()
                .id(UUID.randomUUID())
                .accountHolderId(holderId)
                .credentialType(CredentialType.TOTP)
                .encoderId("totp-sha1")
                .secretReference("sealed-ref")
                .keyVersion((short) 1)
                .enrolledAt(NOW)
                .build();
        when(totpCredentialFactory.create(holderId, NOW))
                .thenReturn(new TotpCredentialFactory.EnrolledTotp(built, rawSecret));
        when(contactChannelRepository.findCurrentPrimary(any(), any())).thenReturn(Optional.empty());

        MfaService.EnrolledTotp result = service.enrollTotp(holderId, NOW);

        verify(authCredentialRepository).save(built);
        assertThat(result.base32Secret()).isEqualTo(TotpUtils.base32Encode(rawSecret));
        assertThat(result.provisioningUri()).startsWith("otpauth://totp/");
    }

    @Test
    void disablingWithNoActiveCredentialFails() {
        when(authCredentialRepository.findActive(holderId, CredentialType.TOTP.name()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disableTotp(holderId, NOW))
                .isInstanceOf(NoActiveTotpCredentialException.class);
    }

    @Test
    void steppingUpWithAValidCodeIssuesAProof() {
        byte[] secret = TotpUtils.generateSecret();
        AuthCredential credential = AuthCredential.builder()
                .id(UUID.randomUUID())
                .accountHolderId(holderId)
                .credentialType(CredentialType.TOTP)
                .encoderId("totp-sha1")
                .secretReference("sealed-ref")
                .keyVersion((short) 1)
                .enrolledAt(NOW.minusSeconds(3600))
                .build();
        when(authCredentialRepository.findActive(holderId, CredentialType.TOTP.name()))
                .thenReturn(Optional.of(credential));
        when(secretBox.open("sealed-ref", (short) 1)).thenReturn(secret);
        String validCode = TotpUtils.generateCode(secret, NOW, 6, Duration.ofSeconds(30));
        AuthToken issuedToken = AuthToken.builder()
                .id(UUID.randomUUID())
                .accountHolderId(holderId)
                .type(AuthTokenType.STEP_UP)
                .tokenHash(HashUtils.sha256Hex("raw-proof"))
                .expiresAt(NOW.plus(Duration.ofMinutes(5)))
                .build();
        when(authTokenFactory.create(holderId, AuthTokenType.STEP_UP, NOW, Duration.ofMinutes(5)))
                .thenReturn(new AuthTokenFactory.IssuedToken(issuedToken, "raw-proof"));

        String proof = service.stepUp(holderId, validCode, NOW);

        assertThat(proof).isEqualTo("raw-proof");
        assertThat(credential.getLastUsedAt()).isEqualTo(NOW);
        verify(authTokenRepository).save(issuedToken);
    }

    @Test
    void steppingUpWithAnInvalidCodeFails() {
        byte[] secret = TotpUtils.generateSecret();
        AuthCredential credential = AuthCredential.builder()
                .id(UUID.randomUUID())
                .accountHolderId(holderId)
                .credentialType(CredentialType.TOTP)
                .encoderId("totp-sha1")
                .secretReference("sealed-ref")
                .keyVersion((short) 1)
                .enrolledAt(NOW.minusSeconds(3600))
                .build();
        when(authCredentialRepository.findActive(holderId, CredentialType.TOTP.name()))
                .thenReturn(Optional.of(credential));
        when(secretBox.open("sealed-ref", (short) 1)).thenReturn(secret);

        assertThatThrownBy(() -> service.stepUp(holderId, "000000", NOW))
                .isInstanceOf(InvalidTotpCodeException.class);
        verify(authTokenRepository, never()).save(any());
    }

    @Test
    void consumingAValidProofSucceedsOnlyOnce() {
        AuthToken token = AuthToken.builder()
                .id(UUID.randomUUID())
                .accountHolderId(holderId)
                .type(AuthTokenType.STEP_UP)
                .tokenHash(HashUtils.sha256Hex("raw-proof"))
                .expiresAt(NOW.plusSeconds(60))
                .build();
        when(authTokenRepository.findByTokenHashAndType(
                HashUtils.sha256Hex("raw-proof"), AuthTokenType.STEP_UP))
                .thenReturn(Optional.of(token));

        service.consumeStepUpProof(holderId, "raw-proof", NOW);

        assertThat(token.getConsumedAt()).isEqualTo(NOW);
        verify(authTokenRepository).save(token);
    }

    @Test
    void consumingAProofForAnotherHolderFails() {
        AuthToken token = AuthToken.builder()
                .id(UUID.randomUUID())
                .accountHolderId(UUID.randomUUID())
                .type(AuthTokenType.STEP_UP)
                .tokenHash(HashUtils.sha256Hex("raw-proof"))
                .expiresAt(NOW.plusSeconds(60))
                .build();
        when(authTokenRepository.findByTokenHashAndType(
                HashUtils.sha256Hex("raw-proof"), AuthTokenType.STEP_UP))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.consumeStepUpProof(holderId, "raw-proof", NOW))
                .isInstanceOf(InvalidStepUpProofException.class);
    }

    private static AccountHolder activeHolder() {
        return AccountHolder.builder()
                .status(AccountHolderStatus.ACTIVE)
                .displayName("Test Holder")
                .build();
    }
}
