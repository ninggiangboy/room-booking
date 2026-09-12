package dev.ngb.backend.repository;

import dev.ngb.backend.model.InventoryReleaseInstruction;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads instructions to hand nights back to the calendar.
 *
 * <p>The claim read exists so a worker can check before acting: a release that has already been applied
 * must not be applied again, because the resource may have been re-sold since.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code inventory_release_instructions}.</p>
 */
public interface InventoryReleaseInstructionRepository extends ListCrudRepository<InventoryReleaseInstruction, UUID> {

    /**
     * Claims a batch of releases for one worker.
     *
     * <pre>{@code
     * SELECT *
     * FROM inventory_release_instructions
     * WHERE state IN ('PENDING', 'FAILED')
     * ORDER BY created_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Must be called inside a transaction. {@code SKIP LOCKED} lets several workers drain the queue
     * without contending, and the unique key on decision and claim means a release applied twice is
     * refused by the database rather than by hope.</p>
     *
     * @param batchSize maximum rows to claim
     * @return possibly empty list of locked instructions, oldest first
     */
    @Query("""
            SELECT *
            FROM inventory_release_instructions
            WHERE state IN ('PENDING', 'FAILED')
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<InventoryReleaseInstruction> claimPending(@Param("batchSize") int batchSize);

    /**
     * Finds the release already written for a decision and claim.
     *
     * <p>Spring derives {@code WHERE cancellation_decision_id = ? AND inventory_claim_id = ?}, matching
     * {@code uk_inventory_release_instructions_claim}.</p>
     *
     * @param cancellationDecisionId decision that ordered the release
     * @param inventoryClaimId claim to be given back
     * @return the instruction, when one exists
     */
    Optional<InventoryReleaseInstruction> findByCancellationDecisionIdAndInventoryClaimId(
            UUID cancellationDecisionId, UUID inventoryClaimId);

    /**
     * Returns every release a decision ordered.
     *
     * <p>Spring derives {@code WHERE cancellation_decision_id = ? ORDER BY created_at}.</p>
     *
     * @param cancellationDecisionId decision whose releases are wanted
     * @return possibly empty list, oldest first
     */
    List<InventoryReleaseInstruction> findAllByCancellationDecisionIdOrderByCreatedAt(
            UUID cancellationDecisionId);
}
