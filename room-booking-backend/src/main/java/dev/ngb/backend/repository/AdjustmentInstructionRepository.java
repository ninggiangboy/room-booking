package dev.ngb.backend.repository;

import dev.ngb.backend.model.AdjustmentInstruction;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads everything a cancellation set in motion that is not a refund.
 *
 * <p>The dispatch queue is claimed per target domain, so a backlog in one does not stall the others.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code adjustment_instructions}.</p>
 */
public interface AdjustmentInstructionRepository extends ListCrudRepository<AdjustmentInstruction, UUID> {

    /**
     * Claims a batch of adjustments bound for one domain.
     *
     * <pre>{@code
     * SELECT *
     * FROM adjustment_instructions
     * WHERE target_domain = :targetDomain
     *   AND state IN ('PENDING', 'FAILED')
     * ORDER BY created_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Must be called inside a transaction. The unique key on decision, type and position means a
     * replayed commit cannot open a second host recovery for the same cancellation, whatever the
     * dispatcher does.</p>
     *
     * @param targetDomain domain to drain the queue for
     * @param batchSize maximum rows to claim
     * @return possibly empty list of locked instructions, oldest first
     */
    @Query("""
            SELECT *
            FROM adjustment_instructions
            WHERE target_domain = :targetDomain
              AND state IN ('PENDING', 'FAILED')
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<AdjustmentInstruction> claimDispatchable(
            @Param("targetDomain") String targetDomain, @Param("batchSize") int batchSize);

    /**
     * Finds the adjustment a retried dispatch already wrote.
     *
     * <p>Spring derives {@code WHERE downstream_idempotency_key = ?}, matching
     * {@code uk_adjustment_instructions_key}.</p>
     *
     * @param downstreamIdempotencyKey key the target domain deduplicates on
     * @return the instruction, when one exists
     */
    Optional<AdjustmentInstruction> findByDownstreamIdempotencyKey(String downstreamIdempotencyKey);

    /**
     * Returns every adjustment a decision produced.
     *
     * <p>Spring derives {@code WHERE cancellation_decision_id = ? ORDER BY created_at}.</p>
     *
     * @param cancellationDecisionId decision whose adjustments are wanted
     * @return possibly empty list, oldest first
     */
    List<AdjustmentInstruction> findAllByCancellationDecisionIdOrderByCreatedAt(
            UUID cancellationDecisionId);
}
