package dev.ngb.backend.ml.internal.repository.feature;

import dev.ngb.backend.ml.internal.model.feature.OnlineFeatureValue;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and rebuilds the current served value of a feature.
 *
 * <p>A rebuildable projection, not evidence. The serving path treats an expired row as missing
 * rather than as an old number, which is why the expiry is read here rather than inferred.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code online_feature_values}.</p>
 */
public interface OnlineFeatureValueRepository extends ListCrudRepository<OnlineFeatureValue, UUID> {

    /**
     * Resolves the current served value, treating an expired row as absent.
     *
     * <pre>{@code
     * SELECT * FROM online_feature_values
     * WHERE feature_definition_id = :featureDefinitionId
     *   AND entity_pseudonym = :entityPseudonym
     *   AND expires_at > :now
     * }</pre>
     *
     * @param featureDefinitionId the feature version
     * @param entityPseudonym the entity
     * @param now the serving instant, from the application clock
     * @return the current value, when one is present and unexpired
     */
    @Query("""
            SELECT * FROM online_feature_values
            WHERE feature_definition_id = :featureDefinitionId
              AND entity_pseudonym = :entityPseudonym
              AND expires_at > :now
            """)
    Optional<OnlineFeatureValue> findCurrent(
            @Param("featureDefinitionId") UUID featureDefinitionId,
            @Param("entityPseudonym") String entityPseudonym, @Param("now") Instant now);

    /**
     * Lists the served values of one feature, for a bounded rebuild.
     *
     * @param featureDefinitionId the feature version
     * @return possibly empty list
     */
    List<OnlineFeatureValue> findByFeatureDefinitionId(UUID featureDefinitionId);

    /**
     * Removes expired rows, which the serving path already ignores.
     *
     * <pre>{@code
     * DELETE FROM online_feature_values WHERE expires_at <= :now
     * }</pre>
     *
     * @param now the sweep instant, from the application clock
     * @return how many rows were removed
     */
    @Query("DELETE FROM online_feature_values WHERE expires_at <= :now")
    int deleteExpired(@Param("now") Instant now);

    /**
     * Removes one subject's served values, which is how a suppression takes effect immediately
     * rather than at the next rebuild.
     *
     * <pre>{@code
     * DELETE FROM online_feature_values WHERE entity_pseudonym = :entityPseudonym
     * }</pre>
     *
     * @param entityPseudonym the subject
     * @return how many rows were removed
     */
    @Query("DELETE FROM online_feature_values WHERE entity_pseudonym = :entityPseudonym")
    int deleteForSubject(@Param("entityPseudonym") String entityPseudonym);
}
