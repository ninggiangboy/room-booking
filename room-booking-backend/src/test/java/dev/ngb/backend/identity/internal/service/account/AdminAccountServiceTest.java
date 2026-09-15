package dev.ngb.backend.identity.internal.service.account;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.ngb.backend.identity.internal.exception.InvalidAccountStatusTransitionException;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.identity.internal.repository.account.AccountHolderRepository;
import dev.ngb.backend.identity.internal.repository.session.AuthTokenRepository;
import dev.ngb.backend.identity.internal.service.auth.session.RefreshTokenService;
import dev.ngb.backend.platform.AuditTrailWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private AccountHolderFinder accountHolderFinder;
    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private AuthTokenRepository authTokenRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AuditTrailWriter auditTrailWriter;

    private AdminAccountService service;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AdminAccountService(
                accountHolderFinder, accountHolderRepository, authTokenRepository,
                refreshTokenService, auditTrailWriter, clock);
        adminId = UUID.randomUUID();
    }

    @Test
    void suspendingAnActiveHolderRevokesSessionsAndTokensAndAudits() {
        AccountHolder holder = activeHolder();
        when(accountHolderFinder.findByIdForUpdate(holder.getId())).thenReturn(holder);
        when(authTokenRepository.findAllByAccountHolderIdAndConsumedAtIsNull(holder.getId()))
                .thenReturn(List.of());

        service.updateStatus(holder.getId(), AccountHolderStatus.SUSPENDED, "FRAUD_REVIEW", adminId);

        assertThat(holder.getStatus()).isEqualTo(AccountHolderStatus.SUSPENDED);
        verify(accountHolderRepository).save(holder);
        verify(refreshTokenService).revokeAllSessionsForHolder(holder.getId(), NOW, "FRAUD_REVIEW");
        verify(auditTrailWriter).record(any());
    }

    @Test
    void reactivatingASuspendedHolderDoesNotRevokeSessions() {
        AccountHolder holder = holderWithStatus(AccountHolderStatus.SUSPENDED);
        when(accountHolderFinder.findByIdForUpdate(holder.getId())).thenReturn(holder);

        service.updateStatus(holder.getId(), AccountHolderStatus.ACTIVE, "APPEAL_UPHELD", adminId);

        assertThat(holder.getStatus()).isEqualTo(AccountHolderStatus.ACTIVE);
        verify(refreshTokenService, never()).revokeAllSessionsForHolder(any(), any(), any());
        verify(auditTrailWriter).record(any());
    }

    @Test
    void requestingTheCurrentStatusIsANoOp() {
        AccountHolder holder = activeHolder();
        when(accountHolderFinder.findByIdForUpdate(holder.getId())).thenReturn(holder);

        service.updateStatus(holder.getId(), AccountHolderStatus.ACTIVE, "NOOP", adminId);

        verify(accountHolderRepository, never()).save(any());
        verifyNoInteractions(auditTrailWriter);
    }

    @Test
    void closedHoldersCannotBeMovedThroughThisEndpoint() {
        AccountHolder holder = holderWithStatus(AccountHolderStatus.CLOSED);
        when(accountHolderFinder.findByIdForUpdate(holder.getId())).thenReturn(holder);

        assertThatThrownBy(() ->
                service.updateStatus(holder.getId(), AccountHolderStatus.ACTIVE, "REASON", adminId))
                .isInstanceOf(InvalidAccountStatusTransitionException.class);
        verify(accountHolderRepository, never()).save(any());
    }

    @Test
    void closedCannotBeRequestedThroughThisEndpoint() {
        AccountHolder holder = activeHolder();
        when(accountHolderFinder.findByIdForUpdate(holder.getId())).thenReturn(holder);

        assertThatThrownBy(() ->
                service.updateStatus(holder.getId(), AccountHolderStatus.CLOSED, "REASON", adminId))
                .isInstanceOf(InvalidAccountStatusTransitionException.class);
    }

    private static AccountHolder activeHolder() {
        return holderWithStatus(AccountHolderStatus.ACTIVE);
    }

    private static AccountHolder holderWithStatus(AccountHolderStatus status) {
        return AccountHolder.builder()
                .id(UUID.randomUUID())
                .displayName("Test Holder")
                .status(status)
                .build();
    }
}
