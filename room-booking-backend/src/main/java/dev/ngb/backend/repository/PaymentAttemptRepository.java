package dev.ngb.backend.repository;

import dev.ngb.backend.model.PaymentAttempt;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads guest journeys toward satisfying an obligation.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payment_attempts}.</p>
 */
public interface PaymentAttemptRepository extends ListCrudRepository<PaymentAttempt, UUID> {

    /**
     * Returns an obligation's attempts, most recent first.
     *
     * <p>Spring derives {@code WHERE obligation_id = ? ORDER BY attempt_number DESC}.</p>
     *
     * @param obligationId obligation whose attempts are wanted
     * @return possibly empty list of attempts
     */
    List<PaymentAttempt> findAllByObligationIdOrderByAttemptNumberDesc(UUID obligationId);

    /**
     * Returns the highest attempt number used against an obligation.
     *
     * <pre>{@code
     * SELECT coalesce(max(attempt_number), 0)
     * FROM payment_attempts
     * WHERE obligation_id = :obligationId
     * }</pre>
     *
     * <p>Resolved in the database rather than by counting rows in memory, so two concurrent callers
     * competing for the next number collide on {@code uk_payment_attempts_number} instead of silently
     * agreeing on the same one.</p>
     *
     * @param obligationId obligation whose attempt history is wanted
     * @return the highest number used, or zero when there are none
     */
    @Query("""
            SELECT coalesce(max(attempt_number), 0)
            FROM payment_attempts
            WHERE obligation_id = :obligationId
            """)
    short findHighestAttemptNumber(@Param("obligationId") UUID obligationId);

    /**
     * Returns attempts still waiting on a guest whose deadline has passed.
     *
     * <pre>{@code
     * SELECT *
     * FROM payment_attempts
     * WHERE state = 'REQUIRES_ACTION'
     *   AND action_deadline_at <= :decisionInstant
     * ORDER BY action_deadline_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>A guest who closed the tab never tells the platform so. An abandoned authentication is found
     * here, by its deadline, or it holds inventory until somebody notices.</p>
     *
     * @param decisionInstant the sweep's single decision instant
     * @param batchSize maximum number of attempts to return
     * @return possibly empty list, most overdue first
     */
    @Query("""
            SELECT *
            FROM payment_attempts
            WHERE state = 'REQUIRES_ACTION'
              AND action_deadline_at <= :decisionInstant
            ORDER BY action_deadline_at
            LIMIT :batchSize
            """)
    List<PaymentAttempt> findAbandonedActions(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);
}
