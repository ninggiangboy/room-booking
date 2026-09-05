package dev.ngb.backend.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.event.EmailVerificationIssued;
import dev.ngb.backend.exception.EmailVerificationRateLimitException;
import dev.ngb.backend.model.AuthToken;
import dev.ngb.backend.model.AuthTokenType;
import dev.ngb.backend.model.User;
import dev.ngb.backend.model.UserStatus;
import dev.ngb.backend.repository.AuthTokenRepository;
import dev.ngb.backend.repository.UserRepository;
import dev.ngb.backend.repository.UserRoleRepository;
import dev.ngb.backend.service.user.UserFinder;
import dev.ngb.backend.service.validation.UserAccountPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** Verifies cooldown and rolling-window enforcement for verification-email requests. */
class EmailVerificationServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");

    private AuthTokenRepository authTokenRepository;
    private UserRepository userRepository;
    private ApplicationEventPublisher eventPublisher;
    private EmailVerificationService service;

    /** Creates an isolated service using a fixed clock and mocked persistence collaborators. */
    @BeforeEach
    void setUp() {
        authTokenRepository = mock(AuthTokenRepository.class);
        userRepository = mock(UserRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new EmailVerificationService(
                authTokenRepository,
                userRepository,
                mock(UserRoleRepository.class),
                mock(UserFinder.class),
                new UserAccountPolicy(),
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "tokenTtl", Duration.ofHours(24));
        ReflectionTestUtils.setField(service, "requestCooldown", Duration.ofSeconds(60));
        ReflectionTestUtils.setField(service, "rateLimitWindow", Duration.ofHours(1));
        ReflectionTestUtils.setField(service, "rateLimitMaxRequests", 5);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(activeUser()));
    }

    /** A second request during the cooldown is rejected with an exact retry delay. */
    @Test
    void requestVerificationRejectsRequestDuringCooldown() {
        when(recentTokens()).thenReturn(List.of(tokenCreatedAt(NOW.minusSeconds(30))));

        EmailVerificationRateLimitException exception = assertThrows(
                EmailVerificationRateLimitException.class,
                () -> service.requestVerification(USER_ID));

        assertEquals(30L, exception.getData().get("retryAfterSeconds"));
        assertEquals(NOW.plusSeconds(30), exception.getData().get("retryAt"));
        verify(authTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    /** Five requests inside one hour are rejected even after the short cooldown elapsed. */
    @Test
    void requestVerificationRejectsRequestAtRollingQuota() {
        when(recentTokens()).thenReturn(List.of(
                tokenCreatedAt(NOW.minusSeconds(59 * 60)),
                tokenCreatedAt(NOW.minusSeconds(45 * 60)),
                tokenCreatedAt(NOW.minusSeconds(30 * 60)),
                tokenCreatedAt(NOW.minusSeconds(15 * 60)),
                tokenCreatedAt(NOW.minusSeconds(2 * 60))));

        EmailVerificationRateLimitException exception = assertThrows(
                EmailVerificationRateLimitException.class,
                () -> service.requestVerification(USER_ID));

        assertEquals(60L, exception.getData().get("retryAfterSeconds"));
        assertEquals(NOW.plusSeconds(60), exception.getData().get("retryAt"));
        verify(authTokenRepository, never()).save(any());
    }

    /** A request outside both limits creates a new token and publishes its delivery event. */
    @Test
    void requestVerificationIssuesTokenWhenLimitsPermit() {
        when(recentTokens()).thenReturn(List.of(tokenCreatedAt(NOW.minusSeconds(120))));
        when(authTokenRepository.findAllByUserIdAndTypeAndConsumedAtIsNull(
                USER_ID, AuthTokenType.EMAIL_VERIFICATION)).thenReturn(List.of());

        service.requestVerification(USER_ID);

        verify(authTokenRepository).save(any(AuthToken.class));
        verify(eventPublisher).publishEvent(any(EmailVerificationIssued.class));
    }

    private List<AuthToken> recentTokens() {
        return authTokenRepository
                .findAllByUserIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        USER_ID,
                        AuthTokenType.EMAIL_VERIFICATION,
                        NOW.minus(Duration.ofHours(1)));
    }

    private static User activeUser() {
        return User.builder()
                .id(USER_ID)
                .email("guest@example.com")
                .passwordHash("unused")
                .displayName("Guest")
                .status(UserStatus.ACTIVE)
                .createdAt(NOW.minus(Duration.ofDays(1)))
                .updatedAt(NOW.minus(Duration.ofDays(1)))
                .build();
    }

    private static AuthToken tokenCreatedAt(Instant createdAt) {
        return AuthToken.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .type(AuthTokenType.EMAIL_VERIFICATION)
                .tokenHash(UUID.randomUUID().toString().replace("-", ""))
                .expiresAt(createdAt.plus(Duration.ofHours(24)))
                .createdAt(createdAt)
                .build();
    }
}
