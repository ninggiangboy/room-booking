package dev.ngb.backend.repository;

import dev.ngb.backend.model.PaymentDisputeStrategy;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what this domain decided about a provider dispute payment owns.
 *
 * <p>Provider observations stay in payment. What is read here is the strategy, the frozen manifest
 * and the authorized submission command.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payment_dispute_strategies}.</p>
 */
public interface PaymentDisputeStrategyRepository extends ListCrudRepository<PaymentDisputeStrategy, UUID> {

    /**
     * Finds the strategy taken on one provider dispute.
     *
     * @param paymentDisputeId dispute payment owns
     * @return the strategy, when one has been recorded
     */
    Optional<PaymentDisputeStrategy> findByPaymentDisputeId(UUID paymentDisputeId);

    /**
     * Claims strategies whose provider deadline is approaching.
     *
     * <pre>{@code
     * SELECT *
     * FROM payment_dispute_strategies
     * WHERE state NOT IN ('SUBMITTED', 'ACCEPTED', 'CLOSED')
     *   AND provider_deadline_at <= :at
     * ORDER BY provider_deadline_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. A missed representment window is money lost with no way back,
     * which is why the deadline is swept rather than watched by hand.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum rows to claim
     * @return possibly empty list, nearest deadline first
     */
    @Query("""
            SELECT *
            FROM payment_dispute_strategies
            WHERE state NOT IN ('SUBMITTED', 'ACCEPTED', 'CLOSED')
              AND provider_deadline_at <= :at
            ORDER BY provider_deadline_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<PaymentDisputeStrategy> claimApproachingDeadline(@Param("at") Instant at,
            @Param("batchSize") int batchSize);
}
