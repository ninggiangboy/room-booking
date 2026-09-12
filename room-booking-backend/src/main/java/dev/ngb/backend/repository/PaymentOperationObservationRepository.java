package dev.ngb.backend.repository;

import dev.ngb.backend.model.PaymentOperationObservation;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the append-only record of what providers said.
 *
 * <p>There are no update or delete paths worth using here: the table rejects both by trigger. A
 * correction is a new observation.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payment_operation_observations}.</p>
 */
public interface PaymentOperationObservationRepository extends ListCrudRepository<PaymentOperationObservation, UUID> {

    /**
     * Returns the complete evidence set for one operation, oldest first.
     *
     * <p>Spring derives {@code WHERE operation_id = ? ORDER BY received_at}. The reducer runs over this
     * list; ordering by receipt rather than by the provider's own timestamp is deliberate, because a
     * provider clock cannot be trusted to order the platform's knowledge.</p>
     *
     * @param operationId operation whose evidence is wanted
     * @return possibly empty list of observations, in the order they arrived
     */
    List<PaymentOperationObservation> findAllByOperationIdOrderByReceivedAt(UUID operationId);

    /**
     * Finds an observation already stored for one provider event.
     *
     * <p>Spring derives {@code WHERE provider_account_id = ? AND provider_event_id = ?}, matching
     * {@code uk_payment_operation_observations_event}.</p>
     *
     * @param providerAccountId merchant account the evidence came from
     * @param providerEventId provider event identifier
     * @return the observation, when one was already stored
     */
    Optional<PaymentOperationObservation> findByProviderAccountIdAndProviderEventId(
            UUID providerAccountId, String providerEventId);

    /**
     * Returns provider money nobody has accounted for, oldest first.
     *
     * <pre>{@code
     * SELECT *
     * FROM payment_operation_observations
     * WHERE reducer_outcome IN ('UNMAPPED', 'QUARANTINED')
     *   AND received_at <= :decisionInstant
     * ORDER BY received_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>These become reconciliation cases. An orphan is never attached to a booking by resemblance,
     * so this read exists to get them in front of a person, not to guess.</p>
     *
     * @param decisionInstant the sweep's single decision instant
     * @param batchSize maximum number of observations to return
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT *
            FROM payment_operation_observations
            WHERE reducer_outcome IN ('UNMAPPED', 'QUARANTINED')
              AND received_at <= :decisionInstant
            ORDER BY received_at
            LIMIT :batchSize
            """)
    List<PaymentOperationObservation> findUnaccounted(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);
}
