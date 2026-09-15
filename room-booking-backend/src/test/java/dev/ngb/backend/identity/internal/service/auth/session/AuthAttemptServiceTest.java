package dev.ngb.backend.identity.internal.service.auth.session;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.ngb.backend.identity.internal.exception.AuthAttemptRateLimitException;
import dev.ngb.backend.identity.internal.model.session.AuthAttemptOutcome;
import dev.ngb.backend.identity.internal.model.session.AuthAttemptType;
import dev.ngb.backend.identity.internal.repository.session.AuthAttemptRepository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthAttemptServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private AuthAttemptRepository authAttemptRepository;
    @Mock
    private AuthAttemptFactory authAttemptFactory;

    private AuthAttemptService service;

    @BeforeEach
    void setUp() {
        service = new AuthAttemptService(
                authAttemptRepository, authAttemptFactory, 5, Duration.ofMinutes(15), Duration.ofDays(90));
    }

    @Test
    void allowsAnAttemptBelowBothLimits() {
        UUID accountHolderId = UUID.randomUUID();
        when(authAttemptRepository.countRecentFailuresForAccountHolder(
                eq(accountHolderId), anyString(), any())).thenReturn(4L);
        when(authAttemptRepository.countRecentFailuresForIdentifier(
                anyString(), anyString(), any())).thenReturn(0L);

        assertThatCode(() -> service.checkVelocity(
                accountHolderId, "digest", AuthAttemptType.LOGIN, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenTheAccountHasReachedTheLimit() {
        UUID accountHolderId = UUID.randomUUID();
        when(authAttemptRepository.countRecentFailuresForAccountHolder(
                eq(accountHolderId), anyString(), any())).thenReturn(5L);

        assertThatThrownBy(() -> service.checkVelocity(
                accountHolderId, "digest", AuthAttemptType.LOGIN, NOW))
                .isInstanceOf(AuthAttemptRateLimitException.class);
    }

    @Test
    void rejectsWhenTheIdentifierHasReachedTheLimitEvenWithNoResolvedAccount() {
        when(authAttemptRepository.countRecentFailuresForIdentifier(
                eq("digest"), anyString(), any())).thenReturn(5L);

        assertThatThrownBy(() -> service.checkVelocity(null, "digest", AuthAttemptType.LOGIN, NOW))
                .isInstanceOf(AuthAttemptRateLimitException.class);
    }

    @Test
    void recordingBuildsAndSavesThroughTheFactory() {
        UUID accountHolderId = UUID.randomUUID();

        service.record(
                accountHolderId, "digest", AuthAttemptType.LOGIN, AuthAttemptOutcome.SUCCESS, null,
                "sourceHash", "deviceHash", NOW);

        verify(authAttemptFactory).create(
                accountHolderId, "digest", AuthAttemptType.LOGIN, AuthAttemptOutcome.SUCCESS, null,
                "sourceHash", "deviceHash", NOW, Duration.ofDays(90));
        verify(authAttemptRepository).save(any());
    }
}
