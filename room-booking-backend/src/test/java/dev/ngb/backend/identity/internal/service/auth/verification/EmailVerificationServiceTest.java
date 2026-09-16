package dev.ngb.backend.identity.internal.service.auth.verification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import dev.ngb.backend.identity.internal.exception.InvalidEmailVerificationTokenException;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.identity.internal.model.account.AccountHolderType;
import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import dev.ngb.backend.identity.internal.model.account.ContactChannelPurpose;
import dev.ngb.backend.identity.internal.model.account.ContactChannelType;
import dev.ngb.backend.identity.internal.model.account.MarketContextState;
import dev.ngb.backend.identity.internal.model.session.AuthToken;
import dev.ngb.backend.identity.internal.model.session.AuthTokenType;
import dev.ngb.backend.identity.internal.repository.account.AccountHolderRepository;
import dev.ngb.backend.identity.internal.repository.account.ContactChannelRepository;
import dev.ngb.backend.identity.internal.repository.session.AuthTokenRepository;
import dev.ngb.backend.identity.internal.service.account.AccountHolderFinder;
import dev.ngb.backend.identity.internal.service.auth.AuthTokenFactory;
import dev.ngb.backend.identity.internal.service.authz.AuthorizationService;
import dev.ngb.backend.identity.internal.service.authz.CapabilityGrantService;
import dev.ngb.backend.identity.internal.web.VerifyEmailRequest;
import dev.ngb.backend.platform.util.HashUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private AuthTokenRepository authTokenRepository;
    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private ContactChannelRepository contactChannelRepository;
    @Mock
    private AuthTokenFactory authTokenFactory;
    @Mock
    private AccountHolderFinder accountHolderFinder;
    @Mock
    private CapabilityGrantService capabilityGrantService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new EmailVerificationService(
                authTokenRepository, accountHolderRepository, contactChannelRepository,
                authTokenFactory, accountHolderFinder, capabilityGrantService, authorizationService,
                eventPublisher, clock);
        ReflectionTestUtils.setField(service, "tokenTtl", java.time.Duration.ofHours(24));
        ReflectionTestUtils.setField(service, "requestCooldown", java.time.Duration.ofSeconds(60));
        ReflectionTestUtils.setField(service, "rateLimitWindow", java.time.Duration.ofHours(1));
        ReflectionTestUtils.setField(service, "rateLimitMaxRequests", 5);
    }

    @Test
    void verifyingActivatesAPendingVerificationHolder() {
        UUID holderId = UUID.randomUUID();
        String rawToken = "raw-token";
        AuthToken token = AuthToken.builder()
                .id(UUID.randomUUID())
                .accountHolderId(holderId)
                .type(AuthTokenType.EMAIL_VERIFICATION)
                .tokenHash(HashUtils.sha256Hex(rawToken))
                .expiresAt(NOW.plusSeconds(3600))
                .createdAt(NOW.minusSeconds(60))
                .build();
        when(authTokenRepository.findByTokenHashAndType(
                        HashUtils.sha256Hex(rawToken), AuthTokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));

        AccountHolder holder = pendingHolder(holderId);
        when(accountHolderFinder.findActiveById(holderId)).thenReturn(holder);
        when(accountHolderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ContactChannel emailChannel = unverifiedPrimaryEmail(holderId);
        when(contactChannelRepository.findCurrentPrimary(holderId, ContactChannelType.EMAIL.name()))
                .thenReturn(Optional.of(emailChannel));
        when(contactChannelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(capabilityGrantService.effectiveRoleNames(holderId, NOW)).thenReturn(List.of());
        when(authorizationService.effectiveGlobalCapabilities(holderId, NOW)).thenReturn(java.util.Set.of());

        service.verify(new VerifyEmailRequest(rawToken));

        assertThat(holder.getStatus()).isEqualTo(AccountHolderStatus.ACTIVE);
        verify(accountHolderRepository).save(holder);
    }

    @Test
    void verifyingAnAlreadyActiveHolderDoesNotTouchTheHolderRow() {
        UUID holderId = UUID.randomUUID();
        String rawToken = "raw-token";
        AuthToken token = AuthToken.builder()
                .id(UUID.randomUUID())
                .accountHolderId(holderId)
                .type(AuthTokenType.EMAIL_VERIFICATION)
                .tokenHash(HashUtils.sha256Hex(rawToken))
                .expiresAt(NOW.plusSeconds(3600))
                .createdAt(NOW.minusSeconds(60))
                .build();
        when(authTokenRepository.findByTokenHashAndType(
                        HashUtils.sha256Hex(rawToken), AuthTokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));

        AccountHolder holder = activeHolder(holderId);
        when(accountHolderFinder.findActiveById(holderId)).thenReturn(holder);

        ContactChannel emailChannel = unverifiedPrimaryEmail(holderId);
        when(contactChannelRepository.findCurrentPrimary(holderId, ContactChannelType.EMAIL.name()))
                .thenReturn(Optional.of(emailChannel));
        when(contactChannelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(capabilityGrantService.effectiveRoleNames(holderId, NOW)).thenReturn(List.of());
        when(authorizationService.effectiveGlobalCapabilities(holderId, NOW)).thenReturn(java.util.Set.of());

        service.verify(new VerifyEmailRequest(rawToken));

        verify(accountHolderRepository, never()).save(any());
    }

    @Test
    void verifyingWithAnUnknownTokenFails() {
        when(authTokenRepository.findByTokenHashAndType(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(new VerifyEmailRequest("bad-token")))
                .isInstanceOf(InvalidEmailVerificationTokenException.class);
    }

    private static AccountHolder pendingHolder(UUID id) {
        return AccountHolder.builder()
                .id(id)
                .holderType(AccountHolderType.PERSON)
                .displayName("Test User")
                .status(AccountHolderStatus.PENDING_VERIFICATION)
                .contextState(MarketContextState.UNRESOLVED)
                .build();
    }

    private static AccountHolder activeHolder(UUID id) {
        return AccountHolder.builder()
                .id(id)
                .holderType(AccountHolderType.PERSON)
                .displayName("Test User")
                .status(AccountHolderStatus.ACTIVE)
                .contextState(MarketContextState.UNRESOLVED)
                .build();
    }

    private static ContactChannel unverifiedPrimaryEmail(UUID holderId) {
        return ContactChannel.builder()
                .id(UUID.randomUUID())
                .accountHolderId(holderId)
                .channelType(ContactChannelType.EMAIL)
                .normalizedValue("user@example.com")
                .originalValue("user@example.com")
                .purpose(ContactChannelPurpose.ACCOUNT)
                .isPrimary(true)
                .build();
    }
}
