package dev.ngb.backend.repository;

import dev.ngb.backend.model.HostReserve;
import dev.ngb.backend.model.HostReserveState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the reserves held against a host.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code host_reserves}.</p>
 */
public interface HostReserveRepository extends ListCrudRepository<HostReserve, UUID> {

    /**
     * Returns a host's live reserves in one currency.
     *
     * <p>Spring derives {@code WHERE host_account_holder_id = ? AND currency = ? AND state = ?},
     * matching {@code idx_host_reserves_host}.</p>
     *
     * @param hostAccountHolderId host whose reserves are wanted
     * @param currency ISO 4217 code
     * @param state lifecycle to filter on, normally {@code ACTIVE}
     * @return possibly empty list of reserves
     */
    List<HostReserve> findAllByHostAccountHolderIdAndCurrencyAndState(
            UUID hostAccountHolderId, String currency, HostReserveState state);

    /**
     * Locks one reserve for update.
     *
     * <pre>{@code
     * SELECT *
     * FROM host_reserves
     * WHERE id = :id
     * FOR UPDATE
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction.</strong> Reserve rows are locked after payable
     * allocations and before the payout instruction, in the canonical finance lock order.</p>
     *
     * @param id reserve to lock
     * @return the locked reserve, when it exists
     */
    @Query("""
            SELECT *
            FROM host_reserves
            WHERE id = :id
            FOR UPDATE
            """)
    Optional<HostReserve> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Finds reserves whose maturity has arrived.
     *
     * <pre>{@code
     * SELECT *
     * FROM host_reserves
     * WHERE state = 'ACTIVE'
     *   AND matures_at IS NOT NULL
     *   AND matures_at <= :at
     * ORDER BY matures_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Matches {@code idx_host_reserves_maturity}. A reserve that matures and is never released is an
     * unbounded withholding by neglect.</p>
     *
     * @param at instant the sweep is running at
     * @param batchSize most rows to return
     * @return possibly empty list of matured reserves
     */
    @Query("""
            SELECT *
            FROM host_reserves
            WHERE state = 'ACTIVE'
              AND matures_at IS NOT NULL
              AND matures_at <= :at
            ORDER BY matures_at
            LIMIT :batchSize
            """)
    List<HostReserve> findMatured(@Param("at") Instant at, @Param("batchSize") int batchSize);
}
