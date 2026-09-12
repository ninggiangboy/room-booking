package dev.ngb.backend.repository;

import dev.ngb.backend.model.DeliveryAttempt;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the crossings to delivery providers.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code delivery_attempts}.</p>
 */
public interface DeliveryAttemptRepository extends ListCrudRepository<DeliveryAttempt, UUID> {

    /**
     * Returns an intent's attempts, newest first.
     *
     * <p>Spring derives {@code WHERE notification_intent_id = ? ORDER BY attempt_number DESC}.</p>
     *
     * @param notificationIntentId intent being inspected
     * @return possibly empty list
     */
    List<DeliveryAttempt> findAllByNotificationIntentIdOrderByAttemptNumberDesc(
            UUID notificationIntentId);

    /**
     * Finds the attempt a provider callback refers to.
     *
     * <p>Spring derives {@code WHERE provider_account_id = ? AND provider_reference = ?}, matching
     * {@code uk_delivery_attempts_provider_reference}.</p>
     *
     * @param providerAccountId account the callback arrived on
     * @param providerReference the provider's own handle for the send
     * @return the attempt, when one matches
     */
    Optional<DeliveryAttempt> findByProviderAccountIdAndProviderReference(UUID providerAccountId,
                                                                          String providerReference);

    /**
     * Claims attempts whose retry is due.
     *
     * <pre>{@code
     * SELECT *
     * FROM delivery_attempts
     * WHERE state IN ('FAILED', 'UNKNOWN')
     *   AND next_retry_at IS NOT NULL
     *   AND next_retry_at <= :at
     * ORDER BY next_retry_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. An {@code UNKNOWN} attempt is queried or reconciled before a
     * replacement is submitted, because a duplicate notice is a real cost to the recipient.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum attempts to claim
     * @return possibly empty list, most overdue first
     */
    @Query("""
            SELECT *
            FROM delivery_attempts
            WHERE state IN ('FAILED', 'UNKNOWN')
              AND next_retry_at IS NOT NULL
              AND next_retry_at <= :at
            ORDER BY next_retry_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<DeliveryAttempt> claimDueRetries(@Param("at") Instant at, @Param("batchSize") int batchSize);
}
