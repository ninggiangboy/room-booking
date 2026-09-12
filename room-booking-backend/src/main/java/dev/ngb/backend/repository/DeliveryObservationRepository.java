package dev.ngb.backend.repository;

import dev.ngb.backend.model.DeliveryObservation;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads provider evidence about deliveries.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code delivery_observations}.</p>
 */
public interface DeliveryObservationRepository extends ListCrudRepository<DeliveryObservation, UUID> {

    /**
     * Finds an observation already recorded for a provider event.
     *
     * <p>Spring derives {@code WHERE provider_account_id = ? AND provider_event_id = ?}, matching
     * {@code uk_delivery_observations_event}. A replayed webhook resolves here instead of being stored
     * twice.</p>
     *
     * @param providerAccountId account the event arrived on
     * @param providerEventId the provider's event identity
     * @return the observation, when it was already recorded
     */
    Optional<DeliveryObservation> findByProviderAccountIdAndProviderEventId(UUID providerAccountId,
                                                                            String providerEventId);

    /**
     * Returns everything observed about an attempt, oldest first.
     *
     * <p>Spring derives {@code WHERE delivery_attempt_id = ? ORDER BY received_at}. The attempt's state is
     * the reduction of this list by evidence precedence, so the order matters.</p>
     *
     * @param deliveryAttemptId attempt being inspected
     * @return possibly empty list
     */
    List<DeliveryObservation> findAllByDeliveryAttemptIdOrderByReceivedAt(UUID deliveryAttemptId);
}
