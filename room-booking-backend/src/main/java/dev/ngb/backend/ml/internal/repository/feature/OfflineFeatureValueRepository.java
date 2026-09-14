package dev.ngb.backend.ml.internal.repository.feature;

import dev.ngb.backend.ml.internal.model.feature.OfflineFeatureValue;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.ml.internal.model.feature.OfflineFeatureValue;


/**
 * Reads historical feature values for point-in-time lookup.
 *
 * <p>The lookup takes the latest value that was already effective at the example's prediction
 * instant. Reading the current row instead is the mistake this repository exists to prevent: it
 * lets a training example see an edit made after the outcome it is meant to predict.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code offline_feature_values}.</p>
 */
public interface OfflineFeatureValueRepository extends ListCrudRepository<OfflineFeatureValue, UUID> {

    /**
     * Resolves one feature value as it stood at a given instant.
     *
     * <pre>{@code
     * SELECT * FROM offline_feature_values
     * WHERE feature_definition_id = :featureDefinitionId
     *   AND entity_pseudonym = :entityPseudonym
     *   AND valid_from <= :asOf
     *   AND (valid_to IS NULL OR valid_to > :asOf)
     * ORDER BY valid_from DESC
     * LIMIT 1
     * }</pre>
     *
     * @param featureDefinitionId the feature version
     * @param entityPseudonym the entity
     * @param asOf the prediction instant to read as of
     * @return the value effective at that instant, when one qualifies
     */
    @Query("""
            SELECT * FROM offline_feature_values
            WHERE feature_definition_id = :featureDefinitionId
              AND entity_pseudonym = :entityPseudonym
              AND valid_from <= :asOf
              AND (valid_to IS NULL OR valid_to > :asOf)
            ORDER BY valid_from DESC
            LIMIT 1
            """)
    Optional<OfflineFeatureValue> findAsOf(
            @Param("featureDefinitionId") UUID featureDefinitionId,
            @Param("entityPseudonym") String entityPseudonym, @Param("asOf") Instant asOf);

    /**
     * Lists one entity's stored history for one feature, newest first.
     *
     * @param featureDefinitionId the feature version
     * @param entityPseudonym the entity
     * @return possibly empty list, most recently effective first
     */
    List<OfflineFeatureValue> findByFeatureDefinitionIdAndEntityPseudonymOrderByValidFromDesc(
            UUID featureDefinitionId, String entityPseudonym);

    /**
     * Lists everything one pipeline run produced, for reconciliation.
     *
     * @param pipelineRunId the run
     * @return possibly empty list
     */
    List<OfflineFeatureValue> findByPipelineRunId(UUID pipelineRunId);

    /**
     * Deletes one subject's stored values for a feature, which is how an erasure is honoured:
     * the rows are removed rather than edited, because nothing here may be rewritten.
     *
     * <pre>{@code
     * DELETE FROM offline_feature_values
     * WHERE feature_definition_id = :featureDefinitionId AND entity_pseudonym = :entityPseudonym
     * }</pre>
     *
     * @param featureDefinitionId the feature version
     * @param entityPseudonym the subject
     * @return how many rows were removed
     */
    @Query("""
            DELETE FROM offline_feature_values
            WHERE feature_definition_id = :featureDefinitionId AND entity_pseudonym = :entityPseudonym
            """)
    int deleteForSubject(@Param("featureDefinitionId") UUID featureDefinitionId,
            @Param("entityPseudonym") String entityPseudonym);
}
