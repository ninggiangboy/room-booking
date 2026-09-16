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
import dev.ngb.backend.identity.internal.exception.UnknownMarketException;
import dev.ngb.backend.identity.internal.model.account.MarketContextState;
import dev.ngb.backend.market.MarketLookup;
import dev.ngb.backend.market.MarketSummary;
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
 * {@code SUSPENDED}. It also carries {@link #resolveMarket}, the operator tool
 * {@code AccountHolderRepository.findAllByContextStateOrderByCreatedAtAsc} was added for: both are
 * administrator commands that act on one holder by id and leave the same shape of audit trail.</p>
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
    private final MarketLookup marketLookup;
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

    /**
     * Resolves an account holder's market on an operator's command.
     *
     * <p>No workflow infers a market from a currency, phone number, or address — see
     * {@code docs/conventions/04-time-and-clock.md}'s sibling rule against inferring a zone, which
     * applies here for the same reason. An operator names the market explicitly, and this method
     * validates it through {@link MarketLookup#findUsableByCode} rather than trusting the caller,
     * so a holder can never be resolved into a draft, suspended, or retired market. Loads the
     * target with a row lock through {@link AccountHolderFinder#findByIdForUpdate} so a concurrent
     * resolution or status change for the same holder is serialized.</p>
     *
     * @param targetHolderId account holder whose market is being resolved
     * @param marketCode ISO 3166-1 alpha-2 code of the market to resolve into
     * @param reasonCode stable reason recorded on the audit trail
     * @param actingAdminId operator issuing the command
     * @throws UnknownMarketException when the code names no market currently usable for decisions
     */
    @Transactional
    public void resolveMarket(
            UUID targetHolderId, String marketCode, String reasonCode, UUID actingAdminId) {
        AccountHolder holder = accountHolderFinder.findByIdForUpdate(targetHolderId);
        MarketSummary market = marketLookup.findUsableByCode(marketCode)
                .orElseThrow(() -> new UnknownMarketException(marketCode));

        if (holder.getContextState() == MarketContextState.RESOLVED
                && market.marketCode().equals(holder.getMarketCode())) {
            return;
        }

        Instant now = clock.instant();
        holder.setMarketCode(market.marketCode());
        holder.setContextState(MarketContextState.RESOLVED);
        accountHolderRepository.save(holder);

        auditTrailWriter.record(new AuditEntry(
                now,
                "account.market_resolved",
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
