package dev.ngb.backend.identity.internal.service.account;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import dev.ngb.backend.identity.internal.exception.InvalidStepUpProofException;
import dev.ngb.backend.identity.internal.exception.StepUpRequiredException;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.identity.internal.model.credential.AuthCredential;
import dev.ngb.backend.identity.internal.model.credential.CredentialType;
import dev.ngb.backend.identity.internal.repository.account.AccountHolderRepository;
import dev.ngb.backend.identity.internal.repository.account.ContactChannelRepository;
import dev.ngb.backend.identity.internal.repository.credential.AuthCredentialRepository;
import dev.ngb.backend.identity.internal.repository.session.AuthTokenRepository;
import dev.ngb.backend.identity.internal.service.auth.passwordreset.PasswordCredentialRotator;
import dev.ngb.backend.identity.internal.service.auth.session.RefreshTokenService;
import dev.ngb.backend.identity.internal.service.authz.AuthorizationService;
import dev.ngb.backend.identity.internal.service.authz.CapabilityGrantService;
import dev.ngb.backend.identity.internal.service.mfa.MfaService;
import dev.ngb.backend.identity.internal.service.validation.PasswordPolicy;
import dev.ngb.backend.identity.internal.web.ChangePasswordRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private ContactChannelRepository contactChannelRepository;
    @Mock
    private AuthCredentialRepository authCredentialRepository;
    @Mock
    private AuthTokenRepository authTokenRepository;
    @Mock
    private PasswordCredentialRotator passwordCredentialRotator;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private CapabilityGrantService capabilityGrantService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private AccountHolderFinder accountHolderFinder;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordPolicy passwordPolicy;
    @Mock
    private MfaService mfaService;

    private UserAccountService service;
    private UUID holderId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new UserAccountService(
                accountHolderRepository, contactChannelRepository, authCredentialRepository,
                authTokenRepository, passwordCredentialRotator, refreshTokenService,
                capabilityGrantService, authorizationService, accountHolderFinder, passwordEncoder,
                passwordPolicy, mfaService, clock);
        holderId = UUID.randomUUID();
    }

    @Test
    void changePasswordWithoutTotpEnrolledNeedsNoStepUpProof() {
        AuthCredential currentCredential = AuthCredential.builder()
                .id(UUID.randomUUID())
                .accountHolderId(holderId)
                .credentialType(CredentialType.PASSWORD)
                .verifierDigest("digest")
                .build();
        when(accountHolderFinder.findActiveById(holderId)).thenReturn(AccountHolder.builder().build());
        when(authCredentialRepository.findActive(holderId, CredentialType.PASSWORD.name()))
                .thenReturn(Optional.of(currentCredential));
        when(passwordEncoder.matches("oldPass1!", "digest")).thenReturn(true);
        when(passwordEncoder.matches("newPass1!", "digest")).thenReturn(false);
        when(authCredentialRepository.countByAccountHolderIdAndCredentialTypeAndDisabledAtIsNull(
                holderId, CredentialType.TOTP)).thenReturn(0L);
        AuthCredential enrolledNew = AuthCredential.builder().build();
        when(passwordCredentialRotator.rotate(currentCredential, "newPass1!", NOW))
                .thenReturn(new PasswordCredentialRotator.Rotation(currentCredential, enrolledNew));

        service.changePassword(holderId, new ChangePasswordRequest("oldPass1!", "newPass1!", null));

        verifyNoInteractions(mfaService);
        verify(authCredentialRepository).save(currentCredential);
        verify(authCredentialRepository).save(enrolledNew);
    }

    @Test
    void changePasswordWithTotpEnrolledAndNoProofIsRejected() {
        AuthCredential currentCredential = AuthCredential.builder()
                .id(UUID.randomUUID())
                .accountHolderId(holderId)
                .credentialType(CredentialType.PASSWORD)
                .verifierDigest("digest")
                .build();
        when(accountHolderFinder.findActiveById(holderId)).thenReturn(AccountHolder.builder().build());
        when(authCredentialRepository.findActive(holderId, CredentialType.PASSWORD.name()))
                .thenReturn(Optional.of(currentCredential));
        when(passwordEncoder.matches("oldPass1!", "digest")).thenReturn(true);
        when(passwordEncoder.matches("newPass1!", "digest")).thenReturn(false);
        when(authCredentialRepository.countByAccountHolderIdAndCredentialTypeAndDisabledAtIsNull(
                holderId, CredentialType.TOTP)).thenReturn(1L);

        assertThatThrownBy(() -> service.changePassword(
                holderId, new ChangePasswordRequest("oldPass1!", "newPass1!", null)))
                .isInstanceOf(StepUpRequiredException.class);

        verify(passwordCredentialRotator, never()).rotate(any(), any(), any());
        verifyNoInteractions(mfaService);
    }

    @Test
    void changePasswordWithTotpEnrolledAndInvalidProofPropagatesFailure() {
        AuthCredential currentCredential = AuthCredential.builder()
                .id(UUID.randomUUID())
                .accountHolderId(holderId)
                .credentialType(CredentialType.PASSWORD)
                .verifierDigest("digest")
                .build();
        when(accountHolderFinder.findActiveById(holderId)).thenReturn(AccountHolder.builder().build());
        when(authCredentialRepository.findActive(holderId, CredentialType.PASSWORD.name()))
                .thenReturn(Optional.of(currentCredential));
        when(passwordEncoder.matches("oldPass1!", "digest")).thenReturn(true);
        when(passwordEncoder.matches("newPass1!", "digest")).thenReturn(false);
        when(authCredentialRepository.countByAccountHolderIdAndCredentialTypeAndDisabledAtIsNull(
                holderId, CredentialType.TOTP)).thenReturn(1L);
        org.mockito.Mockito.doThrow(new InvalidStepUpProofException())
                .when(mfaService).consumeStepUpProof(holderId, "bad-proof", NOW);

        assertThatThrownBy(() -> service.changePassword(
                holderId, new ChangePasswordRequest("oldPass1!", "newPass1!", "bad-proof")))
                .isInstanceOf(InvalidStepUpProofException.class);

        verify(passwordCredentialRotator, never()).rotate(any(), any(), any());
    }

    @Test
    void changePasswordWithTotpEnrolledAndValidProofConsumesItAndRotates() {
        AuthCredential currentCredential = AuthCredential.builder()
                .id(UUID.randomUUID())
                .accountHolderId(holderId)
                .credentialType(CredentialType.PASSWORD)
                .verifierDigest("digest")
                .build();
        when(accountHolderFinder.findActiveById(holderId)).thenReturn(AccountHolder.builder().build());
        when(authCredentialRepository.findActive(holderId, CredentialType.PASSWORD.name()))
                .thenReturn(Optional.of(currentCredential));
        when(passwordEncoder.matches("oldPass1!", "digest")).thenReturn(true);
        when(passwordEncoder.matches("newPass1!", "digest")).thenReturn(false);
        when(authCredentialRepository.countByAccountHolderIdAndCredentialTypeAndDisabledAtIsNull(
                holderId, CredentialType.TOTP)).thenReturn(1L);
        AuthCredential enrolledNew = AuthCredential.builder().build();
        when(passwordCredentialRotator.rotate(currentCredential, "newPass1!", NOW))
                .thenReturn(new PasswordCredentialRotator.Rotation(currentCredential, enrolledNew));

        service.changePassword(
                holderId, new ChangePasswordRequest("oldPass1!", "newPass1!", "good-proof"));

        verify(mfaService).consumeStepUpProof(holderId, "good-proof", NOW);
        verify(authCredentialRepository).save(currentCredential);
        verify(authCredentialRepository).save(enrolledNew);
    }

    @Test
    void deletingOwnAccountRequestsDeletionAndRevokesSessionsAndTokens() {
        AccountHolder holder = AccountHolder.builder()
                .id(holderId)
                .status(AccountHolderStatus.ACTIVE)
                .build();
        when(accountHolderFinder.findActiveByIdForUpdate(holderId)).thenReturn(holder);
        when(authTokenRepository.findAllByAccountHolderIdAndConsumedAtIsNull(holderId))
                .thenReturn(java.util.List.of());

        service.deleteOwnAccount(holderId);

        assertThat(holder.getStatus()).isEqualTo(AccountHolderStatus.DELETION_REQUESTED);
        verify(accountHolderRepository).save(holder);
        verify(refreshTokenService).revokeAllSessionsForHolder(holderId, NOW, "ACCOUNT_CLOSED");
    }
}
