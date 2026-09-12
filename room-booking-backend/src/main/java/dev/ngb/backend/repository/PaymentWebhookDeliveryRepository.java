package dev.ngb.backend.repository;

import dev.ngb.backend.model.PaymentWebhookDelivery;
import dev.ngb.backend.model.WebhookDeliveryState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the inbound provider event log.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payment_webhook_deliveries}.</p>
 */
public interface PaymentWebhookDeliveryRepository extends ListCrudRepository<PaymentWebhookDelivery, UUID> {

    /**
     * Finds a delivery already recorded for one provider event.
     *
     * <p>Spring derives {@code WHERE provider_account_id = ? AND provider_event_id = ?}, matching
     * {@code uk_payment_webhook_deliveries_event}. This is the read that turns a redelivery into an
     * acknowledgement instead of a repeated effect.</p>
     *
     * @param providerAccountId merchant account the endpoint resolved to
     * @param providerEventId provider event identifier
     * @return the delivery, when one was already recorded
     */
    Optional<PaymentWebhookDelivery> findByProviderAccountIdAndProviderEventId(
            UUID providerAccountId, String providerEventId);

    /**
     * Claims deliveries waiting to be processed.
     *
     * <pre>{@code
     * SELECT *
     * FROM payment_webhook_deliveries
     * WHERE state IN ('RECEIVED', 'RETRYABLE_FAILED')
     *   AND (next_attempt_at IS NULL OR next_attempt_at <= :decisionInstant)
     * ORDER BY received_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction.</strong> A delivery is marked processed only in
     * the same transaction that applies its effect.</p>
     *
     * @param decisionInstant the worker's single decision instant
     * @param batchSize maximum number of deliveries to claim
     * @return possibly empty list of claimed deliveries
     */
    @Query("""
            SELECT *
            FROM payment_webhook_deliveries
            WHERE state IN ('RECEIVED', 'RETRYABLE_FAILED')
              AND (next_attempt_at IS NULL OR next_attempt_at <= :decisionInstant)
            ORDER BY received_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<PaymentWebhookDelivery> claimPending(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);

    /**
     * Returns deliveries that were abandoned after repeated failure.
     *
     * <p>Spring derives {@code WHERE state = ? ORDER BY received_at}. Callers pass
     * {@code DEAD_LETTER}: these are provider events the platform could not process, and leaving them
     * unread is how a capture goes unrecorded.</p>
     *
     * @param state delivery state to filter on
     * @return possibly empty list, oldest first
     */
    List<PaymentWebhookDelivery> findAllByStateOrderByReceivedAt(WebhookDeliveryState state);
}
