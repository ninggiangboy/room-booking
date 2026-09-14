package dev.ngb.backend.repository;

import dev.ngb.backend.model.StoredValueLot;
import dev.ngb.backend.model.StoredValueLotState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads the individual grants that make up a guest's stored-value balance.
 *
 * <p>Redemption draws down lots in expiry order, which is why the ordering here is part of the
 * contract rather than a convenience.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code stored_value_lots}.</p>
 */
public interface StoredValueLotRepository extends ListCrudRepository<StoredValueLot, UUID> {

    /**
     * Lists the lots in one balance in one state.
     *
     * @param storedValueAccountId the balance
     * @param state where the lots stand
     * @return possibly empty list
     */
    List<StoredValueLot> findByStoredValueAccountIdAndState(UUID storedValueAccountId,
            StoredValueLotState state);

    /**
     * Lists the spendable lots in one balance in the order redemption must consume them: the
     * promise that expires soonest goes first, so the platform never expires value it could have
     * spent.
     *
     * <pre>{@code
     * SELECT * FROM stored_value_lots
     * WHERE stored_value_account_id = :storedValueAccountId
     *   AND state = 'ACTIVE'
     *   AND (expires_at IS NULL OR expires_at > :at)
     * ORDER BY expires_at NULLS LAST, granted_at
     * }</pre>
     *
     * @param storedValueAccountId the balance
     * @param at instant the redemption is happening at
     * @return possibly empty list, soonest to expire first
     */
    @Query("""
            SELECT * FROM stored_value_lots
            WHERE stored_value_account_id = :storedValueAccountId
              AND state = 'ACTIVE'
              AND (expires_at IS NULL OR expires_at > :at)
            ORDER BY expires_at NULLS LAST, granted_at
            """)
    List<StoredValueLot> findSpendable(
            @Param("storedValueAccountId") UUID storedValueAccountId,
            @Param("at") Instant at);

    /**
     * Lists the lots whose expiry has passed, which is the set the expiry sweep takes.
     *
     * <pre>{@code
     * SELECT * FROM stored_value_lots
     * WHERE state = 'ACTIVE' AND expires_at IS NOT NULL AND expires_at <= :at
     * ORDER BY expires_at
     * }</pre>
     *
     * @param at instant the sweep is running for
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT * FROM stored_value_lots
            WHERE state = 'ACTIVE' AND expires_at IS NOT NULL AND expires_at <= :at
            ORDER BY expires_at
            """)
    List<StoredValueLot> findExpirable(@Param("at") Instant at);

    /**
     * Sums what is still spendable in one balance, which must agree with the balance column and is
     * how a drift between the two is found.
     *
     * <pre>{@code
     * SELECT coalesce(sum(remaining_minor), 0) FROM stored_value_lots
     * WHERE stored_value_account_id = :storedValueAccountId AND state = 'ACTIVE'
     * }</pre>
     *
     * @param storedValueAccountId the balance
     * @return total remaining, in integer minor units
     */
    @Query("""
            SELECT coalesce(sum(remaining_minor), 0) FROM stored_value_lots
            WHERE stored_value_account_id = :storedValueAccountId AND state = 'ACTIVE'
            """)
    long sumRemaining(@Param("storedValueAccountId") UUID storedValueAccountId);
}
