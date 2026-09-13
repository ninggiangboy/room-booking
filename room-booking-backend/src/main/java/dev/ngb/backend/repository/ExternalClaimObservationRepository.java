package dev.ngb.backend.repository;

import dev.ngb.backend.model.ExternalClaimObservation;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the normalized provider statements on a claim.
 *
 * <p>Append-only in the database. A statement that would move the claim backwards is stored with its
 * rejection reason rather than discarded, because it is evidence about the provider.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code external_claim_observations}.</p>
 */
public interface ExternalClaimObservationRepository extends ListCrudRepository<ExternalClaimObservation, UUID> {

    /**
     * Lists the observations on a claim, newest first.
     *
     * @param externalClaimId claim
     * @return possibly empty list
     */
    List<ExternalClaimObservation> findByExternalClaimIdOrderByObservationSequenceDesc(
            UUID externalClaimId);

    /**
     * Finds the observation a provider event already produced.
     *
     * @param externalClaimId claim
     * @param providerEventReference provider event identity
     * @return the observation, when the event was already recorded
     */
    Optional<ExternalClaimObservation> findByExternalClaimIdAndProviderEventReference(
            UUID externalClaimId, String providerEventReference);
}
