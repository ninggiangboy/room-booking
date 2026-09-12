package dev.ngb.backend.repository;

import dev.ngb.backend.model.HostRecovery;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what hosts owe the platform.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code host_recoveries}.</p>
 */
public interface HostRecoveryRepository extends ListCrudRepository<HostRecovery, UUID> {

    /**
     * Finds a recovery by the identifier a host or an agent would quote.
     *
     * <p>Spring derives {@code WHERE public_id = ?}, matching {@code uk_host_recoveries_public_id}.</p>
     *
     * @param publicId short public identifier
     * @return the recovery, when one exists
     */
    Optional<HostRecovery> findByPublicId(String publicId);

    /**
     * Finds the recovery a causal instruction already created.
     *
     * <p>Spring derives {@code WHERE causal_source_type = ? AND causal_source_id = ?}, matching
     * {@code uk_host_recoveries_cause}. A replayed chargeback or returned payout resolves here rather
     * than trying to collect the same debt twice.</p>
     *
     * @param causalSourceType kind of instruction that created the debt
     * @param causalSourceId identity of that instruction
     * @return the recovery, when one exists
     */
    Optional<HostRecovery> findByCausalSourceTypeAndCausalSourceId(
            String causalSourceType, UUID causalSourceId);

    /**
     * Returns a host's outstanding debts in one currency.
     *
     * <pre>{@code
     * SELECT *
     * FROM host_recoveries
     * WHERE host_account_holder_id = :hostAccountHolderId
     *   AND currency = :currency
     *   AND state NOT IN ('RECOVERED', 'WRITTEN_OFF')
     * ORDER BY opened_at
     * }</pre>
     *
     * <p>Matches {@code idx_host_recoveries_host}. Read before a payout, because collecting from a
     * payout the host has already been told about is worse than collecting from the next one.</p>
     *
     * @param hostAccountHolderId host being evaluated
     * @param currency ISO 4217 code; no cross-currency offset happens without an approved conversion
     * @return possibly empty list of open recoveries, oldest first
     */
    @Query("""
            SELECT *
            FROM host_recoveries
            WHERE host_account_holder_id = :hostAccountHolderId
              AND currency = :currency
              AND state NOT IN ('RECOVERED', 'WRITTEN_OFF')
            ORDER BY opened_at
            """)
    List<HostRecovery> findOutstanding(
            @Param("hostAccountHolderId") UUID hostAccountHolderId,
            @Param("currency") String currency);

    /**
     * Locks one recovery for update.
     *
     * <pre>{@code
     * SELECT *
     * FROM host_recoveries
     * WHERE id = :id
     * FOR UPDATE
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction.</strong> A recovery offset and a new payout contend
     * here: locked recovery and payable rows are what let each minor unit be consumed exactly once.</p>
     *
     * @param id recovery to lock
     * @return the locked recovery, when it exists
     */
    @Query("""
            SELECT *
            FROM host_recoveries
            WHERE id = :id
            FOR UPDATE
            """)
    Optional<HostRecovery> findByIdForUpdate(@Param("id") UUID id);
}
