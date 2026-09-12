package dev.ngb.backend.repository;

import dev.ngb.backend.model.RefundInstruction;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads entitlements to have money returned.
 *
 * <p>This repository never reports whether money moved. That is {@code RefundExecutionRepository},
 * in the payment domain; the projection stored here is for display and is refreshed from its
 * observations.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code refund_instructions}.</p>
 */
public interface RefundInstructionRepository extends ListCrudRepository<RefundInstruction, UUID> {

    /**
     * Finds the instruction a retried command already issued.
     *
     * <p>Spring derives {@code WHERE idempotency_key = ?}, matching
     * {@code uk_refund_instructions_idempotency}.</p>
     *
     * @param idempotencyKey key the payment domain deduplicates on
     * @return the instruction, when one exists
     */
    Optional<RefundInstruction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Returns a booking's refund entitlements, most recent first.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY created_at DESC}.</p>
     *
     * @param bookingId booking whose refunds are wanted
     * @return possibly empty list, newest first
     */
    List<RefundInstruction> findAllByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /**
     * Returns entitlements the payment domain has not settled, most urgent first.
     *
     * <pre>{@code
     * SELECT *
     * FROM refund_instructions
     * WHERE state IN ('ISSUED', 'ACCEPTED')
     * ORDER BY execution_deadline_at NULLS LAST
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>An entitlement that is still outstanding past its deadline is a guest who has not been paid, and
     * this is the read that finds them. The remedy is chasing the payment domain, never issuing a second
     * instruction.</p>
     *
     * @param batchSize maximum rows to return
     * @return possibly empty list, most urgent first
     */
    @Query("""
            SELECT *
            FROM refund_instructions
            WHERE state IN ('ISSUED', 'ACCEPTED')
            ORDER BY execution_deadline_at NULLS LAST
            LIMIT :batchSize
            """)
    List<RefundInstruction> findOutstanding(@Param("batchSize") int batchSize);

    /**
     * Locks one entitlement for update.
     *
     * <pre>{@code SELECT * FROM refund_instructions WHERE id = :id FOR UPDATE}</pre>
     *
     * <p>Must be called inside a transaction. Taken before withdrawing an entitlement, so that a
     * withdrawal and an execution accepting it cannot both believe they won.</p>
     *
     * @param id instruction to lock
     * @return the locked instruction, when it exists
     */
    @Query("SELECT * FROM refund_instructions WHERE id = :id FOR UPDATE")
    Optional<RefundInstruction> findByIdForUpdate(@Param("id") UUID id);
}
