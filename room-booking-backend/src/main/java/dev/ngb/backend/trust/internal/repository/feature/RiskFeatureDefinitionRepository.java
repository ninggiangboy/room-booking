package dev.ngb.backend.trust.internal.repository.feature;

import dev.ngb.backend.trust.internal.model.feature.RiskFeatureDefinition;
import dev.ngb.backend.trust.internal.model.feature.FeatureDefinitionStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads feature definitions.
 *
 * <p>An approved definition is frozen, so a historical evaluation can be reconstructed by reading the
 * exact version its snapshot names rather than whatever the definition says today.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_feature_definitions}.</p>
 */
public interface RiskFeatureDefinitionRepository extends ListCrudRepository<RiskFeatureDefinition, UUID> {

    /**
     * Finds one version of a feature definition.
     *
     * <pre>{@code
     * SELECT * FROM risk_feature_definitions
     * WHERE feature_key = :featureKey AND feature_version = :featureVersion
     * }</pre>
     *
     * @param featureKey feature key
     * @param featureVersion version
     * @return the definition, when that version exists
     */
    Optional<RiskFeatureDefinition> findByFeatureKeyAndFeatureVersion(String featureKey, int featureVersion);

    /**
     * Lists definitions in one status.
     *
     * <pre>{@code
     * SELECT * FROM risk_feature_definitions WHERE status = :status
     * }</pre>
     *
     * @param status definition status
     * @return possibly empty list
     */
    List<RiskFeatureDefinition> findByStatus(FeatureDefinitionStatus status);

    /**
     * Lists the features that may be read on the critical path.
     *
     * <pre>{@code
     * SELECT * FROM risk_feature_definitions
     * WHERE status = 'APPROVED' AND online_serving = TRUE AND operational_use_permitted = TRUE
     * }</pre>
     *
     * <p>Excludes fairness audit features by construction: a check constraint stops one being both, so
     * the operational allowlist cannot silently acquire a protected attribute.</p>
     *
     * @return possibly empty list
     */
    @Query("""
            SELECT *
            FROM risk_feature_definitions
            WHERE status = 'APPROVED' AND online_serving = TRUE AND operational_use_permitted = TRUE
            """)
    List<RiskFeatureDefinition> findOnlineOperational();
}
