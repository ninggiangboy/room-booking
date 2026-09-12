package dev.ngb.backend.repository;

import dev.ngb.backend.model.HostPayableAllocation;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and claims the money a host is owed.
 *
 * <p>The payout planner locks allocations here before reserving them. Locking is what stops two
 * planners agreeing on the same money; the partial unique index on payout items is what stops them
 * if the locking is ever wrong.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code host_payable_allocations}.</p>
 */
public interface HostPayableAllocationRepository extends ListCrudRepository<HostPayableAllocation, UUID> {

    /**
     * Finds the allocation a journal posting created.
     *
     * <p>Spring derives {@code WHERE source_posting_id = ?}, matching
     * {@code uk_host_payable_allocations_posting}. One posting can only ever make one entitlement.</p>
     *
     * @param sourcePostingId posting that created the liability
     * @return the allocation, when one exists
     */
    Optional<HostPayableAllocation> findBySourcePostingId(UUID sourcePostingId);

    /**
     * Locks one allocation for update.
     *
     * <pre>{@code
     * SELECT *
     * FROM host_payable_allocations
     * WHERE id = :id
     * FOR UPDATE
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction.</strong> Allocations are locked in ascending id
     * order, before hold, reserve, and recovery rows and before the payout instruction, which is the
     * canonical finance lock order.</p>
     *
     * @param id allocation to lock
     * @return the locked allocation, when it exists
     */
    @Query("""
            SELECT *
            FROM host_payable_allocations
            WHERE id = :id
            FOR UPDATE
            """)
    Optional<HostPayableAllocation> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Claims a host's matured, unconsumed money for a payout run.
     *
     * <pre>{@code
     * SELECT *
     * FROM host_payable_allocations
     * WHERE host_account_holder_id = :hostAccountHolderId
     *   AND currency = :currency
     *   AND ownership_state = 'PAYABLE'
     *   AND release_state IN ('SCHEDULED', 'AVAILABLE')
     *   AND remaining_amount_minor > reserved_amount_minor
     *   AND (scheduled_release_at IS NULL OR scheduled_release_at <= :maturedAt)
     * ORDER BY id
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction.</strong> The maturity instant is bound by the
     * caller rather than read from the database clock, so one payout run evaluates every allocation
     * against the same moment. Ordering by id gives the canonical lock order; {@code SKIP LOCKED} lets a
     * second planner move on rather than block. Holds, reserves, recoveries, and destination checks are
     * revalidated separately against their own rows.</p>
     *
     * @param hostAccountHolderId host being paid
     * @param currency ISO 4217 code of the payout
     * @param maturedAt instant the run is planning at
     * @param batchSize most allocations to claim
     * @return the claimed allocations, in lock order
     */
    @Query("""
            SELECT *
            FROM host_payable_allocations
            WHERE host_account_holder_id = :hostAccountHolderId
              AND currency = :currency
              AND ownership_state = 'PAYABLE'
              AND release_state IN ('SCHEDULED', 'AVAILABLE')
              AND remaining_amount_minor > reserved_amount_minor
              AND (scheduled_release_at IS NULL OR scheduled_release_at <= :maturedAt)
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<HostPayableAllocation> claimEligible(
            @Param("hostAccountHolderId") UUID hostAccountHolderId,
            @Param("currency") String currency,
            @Param("maturedAt") Instant maturedAt,
            @Param("batchSize") int batchSize);

    /**
     * Totals what a host could be paid right now, before holds and reserves.
     *
     * <pre>{@code
     * SELECT COALESCE(sum(remaining_amount_minor - reserved_amount_minor), 0)
     * FROM host_payable_allocations
     * WHERE host_account_holder_id = :hostAccountHolderId
     *   AND currency = :currency
     *   AND ownership_state = 'PAYABLE'
     *   AND release_state = 'AVAILABLE'
     * }</pre>
     *
     * <p>Deliberately not called "available to payout". Available-to-payout is this figure less active
     * holds, required reserves, and approved recoveries, and every one of those subtractions has to name
     * its own items rather than being folded into one number here.</p>
     *
     * @param hostAccountHolderId host whose balance is wanted
     * @param currency ISO 4217 code
     * @return matured unreserved minor units, zero when there are none
     */
    @Query("""
            SELECT COALESCE(sum(remaining_amount_minor - reserved_amount_minor), 0)
            FROM host_payable_allocations
            WHERE host_account_holder_id = :hostAccountHolderId
              AND currency = :currency
              AND ownership_state = 'PAYABLE'
              AND release_state = 'AVAILABLE'
            """)
    long sumMaturedUnreserved(
            @Param("hostAccountHolderId") UUID hostAccountHolderId,
            @Param("currency") String currency);

    /**
     * Finds allocations whose scheduled release has arrived.
     *
     * <pre>{@code
     * SELECT *
     * FROM host_payable_allocations
     * WHERE release_state = 'SCHEDULED'
     *   AND scheduled_release_at <= :dueAt
     * ORDER BY scheduled_release_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Matches {@code idx_host_payable_allocations_eligible}. The release worker binds its own instant;
     * no {@code now()} appears in the index predicate, so the index stays usable.</p>
     *
     * @param dueAt instant the worker is evaluating at
     * @param batchSize most rows to return
     * @return possibly empty list of allocations, earliest release first
     */
    @Query("""
            SELECT *
            FROM host_payable_allocations
            WHERE release_state = 'SCHEDULED'
              AND scheduled_release_at <= :dueAt
            ORDER BY scheduled_release_at
            LIMIT :batchSize
            """)
    List<HostPayableAllocation> findDueForRelease(
            @Param("dueAt") Instant dueAt, @Param("batchSize") int batchSize);
}
