package dev.ngb.backend.repository;

import dev.ngb.backend.model.RefundExecution;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the carrying-out of approved refunds.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code refund_executions}.</p>
 */
public interface RefundExecutionRepository extends ListCrudRepository<RefundExecution, UUID> {

    /**
     * Finds the execution already created for a refund instruction.
     *
     * <p>Spring derives {@code WHERE refund_instruction_id = ? AND instruction_version = ?}, matching
     * {@code uk_refund_executions_instruction}. This is the read that stops a replayed instruction from
     * paying the guest twice.</p>
     *
     * @param refundInstructionId instruction being executed
     * @param instructionVersion version of that instruction
     * @return the execution, when one exists
     */
    Optional<RefundExecution> findByRefundInstructionIdAndInstructionVersion(
            UUID refundInstructionId, int instructionVersion);

    /**
     * Returns a booking's refunds, most recent first.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY created_at DESC}.</p>
     *
     * @param bookingId booking whose refunds are wanted
     * @return possibly empty list of executions
     */
    List<RefundExecution> findAllByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /**
     * Returns refunds that still need work, oldest deadline first.
     *
     * <pre>{@code
     * SELECT *
     * FROM refund_executions
     * WHERE state IN ('APPROVED', 'RESERVED', 'SUBMITTING', 'PENDING', 'FAILED_RETRYABLE', 'UNKNOWN')
     * ORDER BY execution_deadline_at NULLS LAST
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>{@code UNKNOWN} is included because such a refund still holds its reservation: it is chased by
     * querying the provider, never by submitting a replacement.</p>
     *
     * @param batchSize maximum number of executions to return
     * @return possibly empty list, most urgent first
     */
    @Query("""
            SELECT *
            FROM refund_executions
            WHERE state IN ('APPROVED', 'RESERVED', 'SUBMITTING', 'PENDING',
                            'FAILED_RETRYABLE', 'UNKNOWN')
            ORDER BY execution_deadline_at NULLS LAST
            LIMIT :batchSize
            """)
    List<RefundExecution> findOutstanding(@Param("batchSize") int batchSize);
}
