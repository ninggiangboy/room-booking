package dev.ngb.backend.repository;

import dev.ngb.backend.model.PersonalizationErasureApplication;
import dev.ngb.backend.model.ErasureTargetStore;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what each derived store did about an erasure directive.
 *
 * <p>A directive is only honoured when every store it reaches has a row here, so these reads answer
 * "what is still outstanding" rather than "was it done".</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code personalization_erasure_applications}.</p>
 */
public interface PersonalizationErasureApplicationRepository extends ListCrudRepository<PersonalizationErasureApplication, UUID> {

    /**
     * Lists what has been recorded for one directive.
     *
     * @param directiveId the directive
     * @return possibly empty list
     */
    List<PersonalizationErasureApplication> findByDirectiveId(UUID directiveId);

    /**
     * Finds one store's record for one directive.
     *
     * @param directiveId the directive
     * @param targetStore the derived store
     * @return the record, when that store has reported
     */
    Optional<PersonalizationErasureApplication> findByDirectiveIdAndTargetStore(UUID directiveId,
            ErasureTargetStore targetStore);

    /**
     * Lists stores that reported anything other than a clean application, for privacy follow-up.
     *
     * <pre>{@code
     * SELECT * FROM personalization_erasure_applications
     * WHERE outcome NOT IN ('APPLIED', 'NOTHING_TO_ERASE')
     * ORDER BY applied_at DESC
     * }</pre>
     *
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM personalization_erasure_applications
            WHERE outcome NOT IN ('APPLIED', 'NOTHING_TO_ERASE')
            ORDER BY applied_at DESC
            """)
    List<PersonalizationErasureApplication> findUnresolved();
}
