package dev.ngb.backend.repository;

import dev.ngb.backend.model.PayoutItem;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads which host money a payout consists of.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payout_items}.</p>
 */
public interface PayoutItemRepository extends ListCrudRepository<PayoutItem, UUID> {

    /**
     * Returns the items of one payout.
     *
     * <p>Spring derives {@code WHERE payout_instruction_id = ?}, matching
     * {@code idx_payout_items_instruction}.</p>
     *
     * @param payoutInstructionId payout whose items are wanted
     * @return possibly empty list of items
     */
    List<PayoutItem> findAllByPayoutInstructionId(UUID payoutInstructionId);

    /**
     * Finds the live item holding an allocation, if any.
     *
     * <pre>{@code
     * SELECT *
     * FROM payout_items
     * WHERE payable_allocation_id = :payableAllocationId
     *   AND state IN ('RESERVED', 'SETTLED', 'RETURNED')
     * }</pre>
     *
     * <p>A partial unique index allows at most one such row, so this returns at most one. A released item
     * is excluded because it has given the allocation back; a returned one is not, because the money
     * already left and comes back through a recovery.</p>
     *
     * @param payableAllocationId allocation being checked
     * @return the item holding it, when one does
     */
    @Query("""
            SELECT *
            FROM payout_items
            WHERE payable_allocation_id = :payableAllocationId
              AND state IN ('RESERVED', 'SETTLED', 'RETURNED')
            """)
    Optional<PayoutItem> findLiveByAllocation(
            @Param("payableAllocationId") UUID payableAllocationId);

    /**
     * Totals what the items of a payout currently claim.
     *
     * <pre>{@code
     * SELECT COALESCE(sum(selected_amount_minor), 0)
     * FROM payout_items
     * WHERE payout_instruction_id = :payoutInstructionId
     *   AND state <> 'RELEASED'
     * }</pre>
     *
     * <p>The database already refuses a committed instruction whose items do not sum to its amount. This
     * exists so the planner can check before it commits and report a useful error rather than a
     * constraint violation.</p>
     *
     * @param payoutInstructionId payout being totalled
     * @return minor units claimed, zero when nothing is
     */
    @Query("""
            SELECT COALESCE(sum(selected_amount_minor), 0)
            FROM payout_items
            WHERE payout_instruction_id = :payoutInstructionId
              AND state <> 'RELEASED'
            """)
    long sumClaimed(@Param("payoutInstructionId") UUID payoutInstructionId);
}
