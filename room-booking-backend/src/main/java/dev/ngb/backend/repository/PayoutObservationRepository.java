package dev.ngb.backend.repository;

import dev.ngb.backend.model.PayoutObservation;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the evidence the provider and the bank supplied.
 *
 * <p>Append-only. There is no amend path, and a contradicting fact is a later row rather than an edit
 * of an earlier one.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payout_observations}.</p>
 */
public interface PayoutObservationRepository extends ListCrudRepository<PayoutObservation, UUID> {

    /**
     * Finds an observation already recorded for a provider event.
     *
     * <p>Spring derives {@code WHERE provider_account_id = ? AND provider_event_id = ?}, matching
     * {@code uk_payout_observations_event}. A redelivered webhook resolves here instead of being applied
     * a second time.</p>
     *
     * @param providerAccountId merchant account the event came from
     * @param providerEventId provider's event identity
     * @return the observation, when one exists
     */
    Optional<PayoutObservation> findByProviderAccountIdAndProviderEventId(
            UUID providerAccountId, String providerEventId);

    /**
     * Returns the evidence for one call, oldest first.
     *
     * <p>Spring derives {@code WHERE payout_operation_id = ? ORDER BY observed_at}, matching
     * {@code idx_payout_observations_operation}.</p>
     *
     * @param payoutOperationId operation whose evidence is wanted
     * @return possibly empty list of observations
     */
    List<PayoutObservation> findAllByPayoutOperationIdOrderByObservedAt(UUID payoutOperationId);

    /**
     * Returns evidence that could not be attached to any call.
     *
     * <pre>{@code
     * SELECT *
     * FROM payout_observations
     * WHERE payout_operation_id IS NULL
     * ORDER BY observed_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Matches {@code idx_payout_observations_orphans}. An orphan is not noise: it may be a transfer
     * the platform made and lost track of, which is precisely the thing reconciliation must surface
     * rather than discard.</p>
     *
     * @param batchSize most rows to return
     * @return possibly empty list of orphaned observations, oldest first
     */
    @Query("""
            SELECT *
            FROM payout_observations
            WHERE payout_operation_id IS NULL
            ORDER BY observed_at
            LIMIT :batchSize
            """)
    List<PayoutObservation> findOrphans(@Param("batchSize") int batchSize);
}
