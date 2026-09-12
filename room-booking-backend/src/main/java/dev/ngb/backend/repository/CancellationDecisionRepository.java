package dev.ngb.backend.repository;

import dev.ngb.backend.model.CancellationDecision;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads committed recalculations of booking contracts.
 *
 * <p>A decision is the evidence that the platform said what it said, so nothing here updates one; the
 * lock method exists for the narrow writes a committed row still allows -- recording the revision it
 * produced, and recording that a correction superseded it.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code cancellation_decisions}.</p>
 */
public interface CancellationDecisionRepository extends ListCrudRepository<CancellationDecision, UUID> {

    /**
     * Finds the decision a retried command already produced.
     *
     * <p>Spring derives {@code WHERE booking_id = ? AND idempotency_key = ?}, matching
     * {@code uk_cancellation_decisions_idempotency}. This is the read that makes a replayed cancel command
     * converge on the decision that exists rather than refund the guest twice.</p>
     *
     * @param bookingId booking the command targeted
     * @param idempotencyKey client key
     * @return the decision, when one exists
     */
    Optional<CancellationDecision> findByBookingIdAndIdempotencyKey(
            UUID bookingId, String idempotencyKey);

    /**
     * Returns the decision that ended a booking, if one has.
     *
     * <pre>{@code
     * SELECT *
     * FROM cancellation_decisions
     * WHERE booking_id = :bookingId
     *   AND status = 'COMMITTED'
     *   AND decision_type IN ('FULL_CANCELLATION', 'NO_SHOW')
     * }</pre>
     *
     * <p>At most one row can match: {@code uk_cancellation_decisions_terminal} is a unique index over
     * {@code booking_id} under exactly this predicate.</p>
     *
     * @param bookingId booking to check
     * @return the terminal decision, when the booking has one
     */
    @Query("""
            SELECT *
            FROM cancellation_decisions
            WHERE booking_id = :bookingId
              AND status = 'COMMITTED'
              AND decision_type IN ('FULL_CANCELLATION', 'NO_SHOW')
            """)
    Optional<CancellationDecision> findTerminal(@Param("bookingId") UUID bookingId);

    /**
     * Locks one decision for update.
     *
     * <pre>{@code SELECT * FROM cancellation_decisions WHERE id = :id FOR UPDATE}</pre>
     *
     * <p>Must be called inside a transaction. A committed decision accepts only two further writes --
     * the revision it produced, set once, and its supersession -- and both take this lock first.</p>
     *
     * @param id decision to lock
     * @return the locked decision, when it exists
     */
    @Query("SELECT * FROM cancellation_decisions WHERE id = :id FOR UPDATE")
    Optional<CancellationDecision> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Returns a booking's decisions, most recent first.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY effective_at DESC}.</p>
     *
     * @param bookingId booking whose decisions are wanted
     * @return possibly empty list, newest first
     */
    List<CancellationDecision> findAllByBookingIdOrderByEffectiveAtDesc(UUID bookingId);
}
