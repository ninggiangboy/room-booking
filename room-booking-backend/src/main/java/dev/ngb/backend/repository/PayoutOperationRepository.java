package dev.ngb.backend.repository;

import dev.ngb.backend.model.PayoutOperation;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads and claims the individual calls made to the payout provider.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payout_operations}.</p>
 */
public interface PayoutOperationRepository extends ListCrudRepository<PayoutOperation, UUID> {

    /**
     * Returns the calls made for one instruction, oldest first.
     *
     * <p>Spring derives {@code WHERE payout_instruction_id = ? ORDER BY created_at}, matching
     * {@code idx_payout_operations_instruction}.</p>
     *
     * @param payoutInstructionId instruction whose calls are wanted
     * @return possibly empty list of operations
     */
    List<PayoutOperation> findAllByPayoutInstructionIdOrderByCreatedAt(UUID payoutInstructionId);

    /**
     * Finds the highest attempt number of a given type for an instruction.
     *
     * <pre>{@code
     * SELECT COALESCE(max(attempt_number), 0)
     * FROM payout_operations
     * WHERE payout_instruction_id = :payoutInstructionId
     *   AND operation_type = :operationType
     * }</pre>
     *
     * <p>Resolved in the database so two workers cannot both read the same number and then both try to
     * insert it; the unique constraint decides which one wins.</p>
     *
     * @param payoutInstructionId instruction being attempted
     * @param operationType kind of call being counted
     * @return the highest attempt number so far, zero when there has been none
     */
    @Query("""
            SELECT COALESCE(max(attempt_number), 0)
            FROM payout_operations
            WHERE payout_instruction_id = :payoutInstructionId
              AND operation_type = :operationType
            """)
    int findHighestAttemptNumber(
            @Param("payoutInstructionId") UUID payoutInstructionId,
            @Param("operationType") String operationType);

    /**
     * Returns calls that left the platform without a proven outcome.
     *
     * <pre>{@code
     * SELECT *
     * FROM payout_operations
     * WHERE state IN ('SUBMITTING', 'SUBMITTED', 'UNKNOWN')
     *   AND submitted_at <= :olderThan
     * ORDER BY submitted_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Matches {@code idx_payout_operations_unresolved}. Each of these is resolved by querying the
     * provider by reference. None of them is resolved by sending the transfer again.</p>
     *
     * @param olderThan only calls submitted at or before this instant
     * @param batchSize most rows to return
     * @return possibly empty list of unresolved operations, oldest first
     */
    @Query("""
            SELECT *
            FROM payout_operations
            WHERE state IN ('SUBMITTING', 'SUBMITTED', 'UNKNOWN')
              AND submitted_at <= :olderThan
            ORDER BY submitted_at
            LIMIT :batchSize
            """)
    List<PayoutOperation> findUnresolved(
            @Param("olderThan") Instant olderThan, @Param("batchSize") int batchSize);

    /**
     * Finds the call a provider reference belongs to.
     *
     * <p>Spring derives {@code WHERE provider_reference = ?}, matching
     * {@code idx_payout_operations_reference}. Evidence that arrives carrying only the provider's own
     * identifier is placed through this.</p>
     *
     * @param providerReference provider's identifier for the transfer
     * @return possibly empty list of matching operations
     */
    List<PayoutOperation> findAllByProviderReference(String providerReference);
}
