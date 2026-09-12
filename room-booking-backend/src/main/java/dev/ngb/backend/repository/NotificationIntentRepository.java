package dev.ngb.backend.repository;

import dev.ngb.backend.model.NotificationIntent;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the decisions to tell somebody something.
 *
 * <p>The claim query is the worker entry point; everything else hangs below an intent.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code notification_intents}.</p>
 */
public interface NotificationIntentRepository extends ListCrudRepository<NotificationIntent, UUID> {

    /**
     * Finds the intent a domain event already produced.
     *
     * <p>Spring derives {@code WHERE source_event_id = ? AND recipient_account_holder_id = ? AND
     * purpose_code = ? AND notification_policy_id = ?}, matching
     * {@code uk_notification_intents_logical}. Reprocessing the same event returns the same intent.</p>
     *
     * @param sourceEventId committed event
     * @param recipientAccountHolderId who is to be told
     * @param purposeCode what the notice is for
     * @param notificationPolicyId policy version that would create it
     * @return the existing intent, when the event was already processed
     */
    Optional<NotificationIntent> findBySourceEventIdAndRecipientAccountHolderIdAndPurposeCodeAndNotificationPolicyId(
            UUID sourceEventId, UUID recipientAccountHolderId, String purposeCode,
            UUID notificationPolicyId);

    /**
     * Claims intents whose send instant has arrived.
     *
     * <pre>{@code
     * SELECT *
     * FROM notification_intents
     * WHERE state IN ('PENDING', 'SCHEDULED', 'READY')
     *   AND scheduled_for <= :at
     * ORDER BY scheduled_for
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. The instant is bound by the caller because an index predicate may
     * not read the clock, and {@code SKIP LOCKED} lets several dispatchers share the queue.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum intents to claim
     * @return possibly empty list, most overdue first
     */
    @Query("""
            SELECT *
            FROM notification_intents
            WHERE state IN ('PENDING', 'SCHEDULED', 'READY')
              AND scheduled_for <= :at
            ORDER BY scheduled_for
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<NotificationIntent> claimDue(@Param("at") Instant at, @Param("batchSize") int batchSize);

    /**
     * Returns intents whose worker lease has lapsed.
     *
     * <pre>{@code
     * SELECT *
     * FROM notification_intents
     * WHERE state = 'DISPATCHING'
     *   AND lease_expires_at <= :at
     * ORDER BY lease_expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>A crashed worker leaves a claim behind; recovering it is safe because the provider idempotency
     * key on the attempt stops a duplicate send.</p>
     *
     * @param at instant to evaluate the lease against
     * @param batchSize maximum intents to return
     * @return possibly empty list, longest lapsed first
     */
    @Query("""
            SELECT *
            FROM notification_intents
            WHERE state = 'DISPATCHING'
              AND lease_expires_at <= :at
            ORDER BY lease_expires_at
            LIMIT :batchSize
            """)
    List<NotificationIntent> findExpiredLeases(@Param("at") Instant at, @Param("batchSize") int batchSize);

    /**
     * Returns what a recipient has been told, newest first.
     *
     * <p>Spring derives {@code WHERE recipient_account_holder_id = ? ORDER BY created_at DESC}.</p>
     *
     * @param recipientAccountHolderId recipient whose notices are wanted
     * @return possibly empty list
     */
    List<NotificationIntent> findAllByRecipientAccountHolderIdOrderByCreatedAtDesc(
            UUID recipientAccountHolderId);
}
