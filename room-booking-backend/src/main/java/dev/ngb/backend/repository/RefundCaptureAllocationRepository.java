package dev.ngb.backend.repository;

import dev.ngb.backend.model.RefundCaptureAllocation;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads which capture each part of a refund draws on.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code refund_capture_allocations}.</p>
 */
public interface RefundCaptureAllocationRepository extends ListCrudRepository<RefundCaptureAllocation, UUID> {

    /**
     * Returns one refund's allocations in the order they are executed.
     *
     * <p>Spring derives {@code WHERE refund_execution_id = ? ORDER BY sequence_number}.</p>
     *
     * @param refundExecutionId refund whose allocations are wanted
     * @return possibly empty list of allocations, in sequence
     */
    List<RefundCaptureAllocation> findAllByRefundExecutionIdOrderBySequenceNumber(
            UUID refundExecutionId);

    /**
     * Returns the minor units currently claimed against one capture.
     *
     * <pre>{@code
     * SELECT coalesce(sum(allocated_amount_minor), 0)
     * FROM refund_capture_allocations
     * WHERE capture_operation_id = :captureOperationId
     *   AND state IN ('RESERVED', 'SUBMITTED', 'SETTLED')
     * }</pre>
     *
     * <p>Summed in the database under a lock on the obligation. Released and failed allocations are
     * excluded because their money went back to the refundable pool.</p>
     *
     * @param captureOperationId capture whose claims are wanted
     * @return minor units claimed, zero when there are none
     */
    @Query("""
            SELECT coalesce(sum(allocated_amount_minor), 0)
            FROM refund_capture_allocations
            WHERE capture_operation_id = :captureOperationId
              AND state IN ('RESERVED', 'SUBMITTED', 'SETTLED')
            """)
    long sumClaimedAgainstCapture(@Param("captureOperationId") UUID captureOperationId);
}
