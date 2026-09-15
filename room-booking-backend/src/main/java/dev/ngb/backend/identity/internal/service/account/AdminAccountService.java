package dev.ngb.backend.identity.internal.service.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.identity.internal.model.session.AuthToken;
import dev.ngb.backend.identity.internal.model.session.TokenConsumptionReason;
import dev.ngb.backend.identity.internal.repository.account.AccountHolderRepository;
import dev.ngb.backend.identity.internal.repository.session.AuthTokenRepository;
import dev.ngb.backend.identity.internal.service.auth.session.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ngb.backend.identity.internal.exception.InvalidAccountStatusTransitionException;
import dev.ngb.backend.platform.ActorType;
import dev.ngb.backend.platform.AuditEntry;
import dev.ngb.backend.platform.AuditOutcome;
import dev.ngb.backend.platform.AuditTrailWriter;


/**
 * Applies administrator-initiated account status changes.
 *
 * <p>{@code @Service} identifies business logic; Lombok generates constructor injection. This is
 * the administrative counterpart to {@link UserAccountService#deleteOwnAccount}: it reuses the same
 * revoke-sessions-and-tokens pattern, but starting from an operator command rather than the holder's
 * own request, and it never sets {@link AccountHolderStatus#CLOSED} — closure stays reachable only
 * through self-service, so this service only moves a holder between {@code ACTIVE} and
 * {@code SUSPENDED}.</p>
 */
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private static final Set<AccountHolderStatus> SUPPORTED_STATUSES =
            Set.of(AccountHolderStatus.ACTIVE, AccountHolderStatus.SUSPENDED);

    private final AccountHolderFinder accountHolderFinder;
    private final AccountHolderRepository accountHolderRepository;
    private final AuthTokenRepository authTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditTrailWriter auditTrailWriter;
    private final Clock clock;

    /**
     * Suspends or reactivates an account on an operator's command.
     *
     * <p>Loads the target with a row lock through {@link AccountHolderFinder#findByIdForUpdate}
     * rather than {@code findActiveByIdForUpdate}, since reactivating a suspended holder requires
     * loading it while it is not active.</p>
     *
     * @param targetHolderId account holder whose status is being changed
     * @param requestedStatus {@code ACTIVE} or {@code SUSPENDED}
     * @param reasonCode stable reason recorded on the audit trail
     * @param actingAdminId operator issuing the command
     * @throws InvalidAccountStatusTransitionException when either the requested status or the
     *     holder's current status is not {@code ACTIVE} or {@code SUSPENDED}
     */
    @Transactional
    public void updateStatus(
            UUID targetHolderId,
            AccountHolderStatus requestedStatus,
            String reasonCode,
            UUID actingAdminId) {
        AccountHolder holder = accountHolderFinder.findByIdForUpdate(targetHolderId);
        if (!SUPPORTED_STATUSES.contains(requestedStatus)
                || !SUPPORTED_STATUSES.contains(holder.getStatus())) {
            throw new InvalidAccountStatusTransitionException(
                    targetHolderId, holder.getStatus(), requestedStatus);
        }
        if (holder.getStatus() == requestedStatus) {
            return;
        }

        Instant now = clock.instant();
        holder.setStatus(requestedStatus);
        accountHolderRepository.save(holder);

        if (requestedStatus == AccountHolderStatus.SUSPENDED) {
            refreshTokenService.revokeAllSessionsForHolder(holder.getId(), now, reasonCode);
            revokeOutstandingTokens(holder.getId(), now);
        }

        auditTrailWriter.record(new AuditEntry(
                now,
                requestedStatus == AccountHolderStatus.SUSPENDED
                        ? "account.suspended"
                        : "account.reactivated",
                "identity",
                "AccountHolder",
                holder.getId(),
                AuditOutcome.ALLOWED,
                reasonCode,
                ActorType.OPERATOR,
                actingAdminId,
                null));
    }

    private void revokeOutstandingTokens(UUID holderId, Instant now) {
        List<AuthToken> tokens = authTokenRepository.findAllByAccountHolderIdAndConsumedAtIsNull(holderId);
        tokens.forEach(token -> token.consume(now, TokenConsumptionReason.REVOKED));
        authTokenRepository.saveAll(tokens);
    }
}
