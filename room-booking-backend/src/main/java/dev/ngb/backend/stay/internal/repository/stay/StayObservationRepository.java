package dev.ngb.backend.stay.internal.repository.stay;

import dev.ngb.backend.stay.internal.model.stay.StayObservation;
import dev.ngb.backend.stay.internal.model.stay.StayObservationSubject;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.stay.internal.model.stay.StayObservation;
import dev.ngb.backend.stay.internal.model.stay.StayObservationSubject;


/**
 * Reads the evidence gathered about a stay.
 *
 * <p>Frozen at insert except for retention and hold. A correction is a further observation, which is
 * why every read here is a list rather than a single current truth.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code stay_observations}.</p>
 */
public interface StayObservationRepository extends ListCrudRepository<StayObservation, UUID> {

    /**
     * Lists everything observed about a stay, newest first.
     *
     * <p>Spring derives {@code WHERE operational_stay_id = ? ORDER BY event_time DESC}.</p>
     *
     * @param operationalStayId stay
     * @return possibly empty list, most recent event first
     */
    List<StayObservation> findByOperationalStayIdOrderByEventTimeDesc(UUID operationalStayId);

    /**
     * Lists what was observed about one aspect of a stay.
     *
     * <p>Spring derives {@code WHERE operational_stay_id = ? AND subject = ? ORDER BY event_time DESC},
     * matching {@code idx_stay_observations_subject}. The completion evaluator reads arrival and
     * departure this way and weighs them; it never takes the newest row as the answer.</p>
     *
     * @param operationalStayId stay
     * @param subject what the observations are about
     * @return possibly empty list, most recent event first
     */
    List<StayObservation> findByOperationalStayIdAndSubjectOrderByEventTimeDesc(UUID operationalStayId,
            StayObservationSubject subject);

    /**
     * Finds an observation by the provider's own reference.
     *
     * <p>Spring derives {@code WHERE provider_account_id = ? AND provider_reference = ?}, matching
     * {@code uk_stay_observations_provider_reference}.</p>
     *
     * @param providerAccountId provider account
     * @param providerReference provider-native identifier
     * @return the observation, when it was already recorded
     */
    Optional<StayObservation> findByProviderAccountIdAndProviderReference(UUID providerAccountId,
            String providerReference);
}
