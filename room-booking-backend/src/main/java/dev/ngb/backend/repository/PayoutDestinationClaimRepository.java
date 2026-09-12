package dev.ngb.backend.repository;

import dev.ngb.backend.model.PayoutDestinationClaim;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads where a host's money may be sent, and whether it may be sent yet.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code payout_destination_claims}. Nothing here says what a host is owed; that
 * is the ledger's business and is unaffected by a destination being rejected.</p>
 */
public interface PayoutDestinationClaimRepository
        extends ListCrudRepository<PayoutDestinationClaim, UUID> {

    /**
     * Finds the live destination for a holder, market, and currency.
     *
     * <pre>{@code
     * SELECT *
     * FROM payout_destination_claims
     * WHERE account_holder_id = :accountHolderId
     *   AND market_code = :marketCode
     *   AND currency = :currency
     *   AND lifecycle_state = 'ACTIVE'
     * }</pre>
     *
     * <p>{@code uk_payout_destination_claims_active} guarantees at most one row matches, which is
     * what stops "where does this payout go" being ambiguous at the moment money moves. The
     * destination may still be unusable — check {@code canReceiveAt} before sending.</p>
     *
     * @param accountHolderId holder being paid
     * @param marketCode market the payout is made in
     * @param currency ISO 4217 currency being sent
     * @return the live destination when one is nominated
     */
    @Query("""
            SELECT *
            FROM payout_destination_claims
            WHERE account_holder_id = :accountHolderId
              AND market_code = :marketCode
              AND currency = :currency
              AND lifecycle_state = 'ACTIVE'
            """)
    Optional<PayoutDestinationClaim> findActive(
            @Param("accountHolderId") UUID accountHolderId,
            @Param("marketCode") String marketCode,
            @Param("currency") String currency);

    /**
     * Finds the destination money may actually be sent to right now.
     *
     * <pre>{@code
     * SELECT *
     * FROM payout_destination_claims
     * WHERE account_holder_id = :accountHolderId
     *   AND market_code = :marketCode
     *   AND currency = :currency
     *   AND lifecycle_state = 'ACTIVE'
     *   AND ownership_state = 'VERIFIED'
     *   AND (cooling_off_until IS NULL OR cooling_off_until <= :decisionInstant)
     * }</pre>
     *
     * <p>All three conditions are pushed into the query on purpose. A caller that loaded the active
     * destination and forgot the cooling-off check would defeat the control that blunts payout
     * diversion after an account takeover.</p>
     *
     * @param accountHolderId holder being paid
     * @param marketCode market the payout is made in
     * @param currency ISO 4217 currency being sent
     * @param decisionInstant the payout run's single decision instant
     * @return the usable destination, when there is one
     */
    @Query("""
            SELECT *
            FROM payout_destination_claims
            WHERE account_holder_id = :accountHolderId
              AND market_code = :marketCode
              AND currency = :currency
              AND lifecycle_state = 'ACTIVE'
              AND ownership_state = 'VERIFIED'
              AND (cooling_off_until IS NULL OR cooling_off_until <= :decisionInstant)
            """)
    Optional<PayoutDestinationClaim> findUsable(
            @Param("accountHolderId") UUID accountHolderId,
            @Param("marketCode") String marketCode,
            @Param("currency") String currency,
            @Param("decisionInstant") Instant decisionInstant);

    /**
     * Returns every destination a holder has ever nominated, newest first.
     *
     * <p>Spring derives {@code WHERE account_holder_id = ? ORDER BY created_at DESC}, including
     * detached and superseded rows, which a payout investigation needs.</p>
     *
     * @param accountHolderId holder whose history is being reviewed
     * @return possibly empty list of destinations, newest first
     */
    List<PayoutDestinationClaim> findAllByAccountHolderIdOrderByCreatedAtDesc(UUID accountHolderId);
}
