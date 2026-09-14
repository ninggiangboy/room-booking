package dev.ngb.backend.stay.internal.repository.access;

import dev.ngb.backend.stay.internal.model.access.AccessObservation;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what locks and their providers reported.
 *
 * <p>Append-only by trigger. The provider event lookup is the deduplication check a webhook handler
 * runs before recording anything.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code access_observations}.</p>
 */
public interface AccessObservationRepository extends ListCrudRepository<AccessObservation, UUID> {

    /**
     * Finds an observation by the provider's own event identity.
     *
     * <p>Spring derives {@code WHERE provider_account_id = ? AND provider_event_id = ?}, matching
     * {@code uk_access_observations_provider_event}. The same callback delivered twice must not become
     * two door openings.</p>
     *
     * @param providerAccountId provider account
     * @param providerEventId provider-native event identity
     * @return the observation, when it was already recorded
     */
    Optional<AccessObservation> findByProviderAccountIdAndProviderEventId(UUID providerAccountId,
            String providerEventId);

    /**
     * Lists what was reported about one grant, newest first.
     *
     * <p>Spring derives {@code WHERE access_grant_id = ? ORDER BY occurred_at DESC}.</p>
     *
     * @param accessGrantId grant
     * @return possibly empty list, most recent event first
     */
    List<AccessObservation> findByAccessGrantIdOrderByOccurredAtDesc(UUID accessGrantId);

    /**
     * Lists what one provider call produced.
     *
     * <p>Spring derives {@code WHERE access_operation_id = ?}.</p>
     *
     * @param accessOperationId operation
     * @return possibly empty list
     */
    List<AccessObservation> findByAccessOperationId(UUID accessOperationId);
}
