package dev.ngb.backend.ml.internal.repository.feature;

import dev.ngb.backend.ml.internal.model.feature.FeatureDefinition;
import dev.ngb.backend.ml.internal.model.DefinitionStatus;
import dev.ngb.backend.ml.internal.model.FeatureEntityKind;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the registry of model inputs.
 *
 * <p>Every feature value, feature set member and serving lookup resolves its meaning here. A
 * reader that cannot find an active definition has to fail rather than serve a value whose
 * contract it does not know.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code feature_definitions}.</p>
 */
public interface FeatureDefinitionRepository extends ListCrudRepository<FeatureDefinition, UUID> {

    /**
     * Finds one exact version of a feature.
     *
     * @param featureKey the feature
     * @param semanticVersion the version
     * @return the definition, when it is registered
     */
    Optional<FeatureDefinition> findByFeatureKeyAndSemanticVersion(String featureKey,
            short semanticVersion);

    /**
     * Lists every version of one feature, newest registration first.
     *
     * @param featureKey the feature
     * @return possibly empty list, most recently created first
     */
    List<FeatureDefinition> findByFeatureKeyOrderByCreatedAtDesc(String featureKey);

    /**
     * Lists the definitions in one lifecycle state for one entity kind.
     *
     * @param status the lifecycle state
     * @param entityKind what the features are keyed by
     * @return possibly empty list
     */
    List<FeatureDefinition> findByStatusAndEntityKind(DefinitionStatus status,
            FeatureEntityKind entityKind);

    /**
     * Lists the features served online, whose freshness and parity the serving path monitors.
     *
     * <pre>{@code
     * SELECT * FROM feature_definitions
     * WHERE available_online_serving AND status IN ('ACTIVE', 'DEPRECATED')
     * ORDER BY feature_key
     * }</pre>
     *
     * @return possibly empty list, by feature key
     */
    @Query("""
            SELECT * FROM feature_definitions
            WHERE available_online_serving AND status IN ('ACTIVE', 'DEPRECATED')
            ORDER BY feature_key
            """)
    List<FeatureDefinition> findOnlineServed();

    /**
     * Lists the features a privacy review has to look at: sensitive traits, and anything
     * carrying personal or restricted data that has been declared eligible for training.
     *
     * <pre>{@code
     * SELECT * FROM feature_definitions
     * WHERE sensitive_attribute OR privacy_class IN ('PERSONAL', 'RESTRICTED')
     * ORDER BY privacy_class DESC, feature_key
     * }</pre>
     *
     * @return possibly empty list, most restricted first
     */
    @Query("""
            SELECT * FROM feature_definitions
            WHERE sensitive_attribute OR privacy_class IN ('PERSONAL', 'RESTRICTED')
            ORDER BY privacy_class DESC, feature_key
            """)
    List<FeatureDefinition> findPrivacyReviewable();

    /**
     * Lists the features that may not inform a consequential decision, because nothing could
     * reconstruct afterwards what value was actually served.
     *
     * <pre>{@code
     * SELECT * FROM feature_definitions
     * WHERE NOT reproducible_offline AND status IN ('ACTIVE', 'DEPRECATED')
     * ORDER BY feature_key
     * }</pre>
     *
     * @return possibly empty list, by feature key
     */
    @Query("""
            SELECT * FROM feature_definitions
            WHERE NOT reproducible_offline AND status IN ('ACTIVE', 'DEPRECATED')
            ORDER BY feature_key
            """)
    List<FeatureDefinition> findNonReproducible();
}
