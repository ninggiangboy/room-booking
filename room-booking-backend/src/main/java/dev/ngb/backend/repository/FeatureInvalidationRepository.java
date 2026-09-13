package dev.ngb.backend.repository;

import dev.ngb.backend.model.FeatureInvalidation;
import dev.ngb.backend.model.FeatureInvalidationReason;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads the records that stop stored feature values being served.
 *
 * <p>The serving path consults these before answering. An invalidation is a decision somebody
 * recorded, not a cache eviction somebody remembered to run.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code feature_invalidations}.</p>
 */
public interface FeatureInvalidationRepository extends ListCrudRepository<FeatureInvalidation, UUID> {

    /**
     * Lists the invalidations in force for one feature and subject at a given instant.
     *
     * <pre>{@code
     * SELECT * FROM feature_invalidations
     * WHERE feature_definition_id = :featureDefinitionId
     *   AND (scope = 'DEFINITION'
     *        OR (scope = 'ENTITY' AND entity_pseudonym = :entityPseudonym))
     *   AND effective_from <= :now
     *   AND (effective_to IS NULL OR effective_to > :now)
     * ORDER BY effective_from DESC
     * }</pre>
     *
     * @param featureDefinitionId the feature version
     * @param entityPseudonym the subject
     * @param now the serving instant, from the application clock
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM feature_invalidations
            WHERE feature_definition_id = :featureDefinitionId
              AND (scope = 'DEFINITION'
                   OR (scope = 'ENTITY' AND entity_pseudonym = :entityPseudonym))
              AND effective_from <= :now
              AND (effective_to IS NULL OR effective_to > :now)
            ORDER BY effective_from DESC
            """)
    List<FeatureInvalidation> findInForce(
            @Param("featureDefinitionId") UUID featureDefinitionId,
            @Param("entityPseudonym") String entityPseudonym, @Param("now") Instant now);

    /**
     * Lists the invalidations recorded for one feature, newest first.
     *
     * @param featureDefinitionId the feature version
     * @return possibly empty list, most recently effective first
     */
    List<FeatureInvalidation> findByFeatureDefinitionIdOrderByEffectiveFromDesc(
            UUID featureDefinitionId);

    /**
     * Lists the invalidations recorded for one reason, for privacy reporting.
     *
     * @param reason why the values stopped being usable
     * @return possibly empty list
     */
    List<FeatureInvalidation> findByReason(FeatureInvalidationReason reason);
}
