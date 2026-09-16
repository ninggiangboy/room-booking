package dev.ngb.backend.identity.internal.service.auth.session;

import java.time.Clock;
import java.time.Duration;
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

import dev.ngb.backend.identity.SessionRevoked;
import dev.ngb.backend.identity.internal.exception.SessionNotFoundException;
import dev.ngb.backend.identity.internal.model.session.AuthSession;
import dev.ngb.backend.identity.internal.model.session.AuthenticationMethod;
import dev.ngb.backend.identity.internal.repository.session.AuthSessionRepository;
import dev.ngb.backend.identity.internal.repository.session.AuthTokenRepository;
import dev.ngb.backend.identity.internal.service.auth.AuthTokenFactory;
import dev.ngb.backend.platform.AssuranceLevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private AuthTokenRepository authTokenRepository;
    @Mock
    private AuthSessionRepository authSessionRepository;
    @Mock
    private AuthTokenFactory authTokenFactory;
    @Mock
    private AuthSessionFactory authSessionFactory;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RefreshTokenService service;
    private UUID accountHolderId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new RefreshTokenService(
                authTokenRepository, authSessionRepository, authTokenFactory, authSessionFactory,
                eventPublisher, clock, Duration.ofDays(30), Duration.ofDays(180));
        accountHolderId = UUID.randomUUID();
    }

    @Test
    void revokingASessionBelongingToTheCallerRevokesItAndItsTokens() {
        AuthSession session = liveSession(accountHolderId);
        when(authSessionRepository.findByIdForUpdate(session.getId()))
                .thenReturn(Optional.of(session));
        when(authTokenRepository.findAllBySessionIdAndConsumedAtIsNull(session.getId()))
                .thenReturn(List.of());

        service.revokeSession(accountHolderId, session.getId(), NOW, "USER_REVOKED_SESSION");

        assertThat(session.getRevokedAt()).isEqualTo(NOW);
        assertThat(session.getRevocationReason()).isEqualTo("USER_REVOKED_SESSION");
        verify(authSessionRepository).save(session);
        verify(eventPublisher).publishEvent(
                new SessionRevoked(session.getId(), accountHolderId, "USER_REVOKED_SESSION", NOW));
    }

    @Test
    void revokingAnAbsentSessionFails() {
        UUID sessionId = UUID.randomUUID();
        when(authSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.revokeSession(accountHolderId, sessionId, NOW, "USER_REVOKED_SESSION"))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void revokingAnotherHoldersSessionFailsTheSameWayAsAnAbsentOne() {
        AuthSession session = liveSession(UUID.randomUUID());
        when(authSessionRepository.findByIdForUpdate(session.getId()))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() ->
                service.revokeSession(accountHolderId, session.getId(), NOW, "USER_REVOKED_SESSION"))
                .isInstanceOf(SessionNotFoundException.class);
        verify(authSessionRepository, never()).save(any());
    }

    @Test
    void revokingAnAlreadyRevokedSessionIsIdempotent() {
        AuthSession session = liveSession(accountHolderId);
        session.revoke(NOW.minusSeconds(60), "LOGOUT", null);
        when(authSessionRepository.findByIdForUpdate(session.getId()))
                .thenReturn(Optional.of(session));

        service.revokeSession(accountHolderId, session.getId(), NOW, "USER_REVOKED_SESSION");

        verify(authSessionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private static AuthSession liveSession(UUID accountHolderId) {
        return AuthSession.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .authenticationMethod(AuthenticationMethod.PASSWORD)
                .assuranceLevel(AssuranceLevel.AAL1)
                .lastAssuranceProofAt(NOW)
                .rotationGeneration(0)
                .lastUsedAt(NOW)
                .idleExpiresAt(NOW.plus(Duration.ofDays(30)))
                .absoluteExpiresAt(NOW.plus(Duration.ofDays(180)))
                .build();
    }
}
