package dev.ngb.backend.trust.internal.repository.feature;

import dev.ngb.backend.trust.internal.model.feature.RiskFeatureSnapshot;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what was actually read at the instant of an evaluation.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_feature_snapshots}.</p>
 */
public interface RiskFeatureSnapshotRepository extends ListCrudRepository<RiskFeatureSnapshot, UUID> {

    /**
     * Finds the snapshot behind one evaluation and feature set.
     *
     * <pre>{@code
     * SELECT * FROM risk_feature_snapshots
     * WHERE evaluation_key = :evaluationKey AND feature_set_reference = :featureSetReference
     * }</pre>
     *
     * @param evaluationKey evaluation key
     * @param featureSetReference feature set that was read
     * @return the snapshot, when one was taken
     */
    Optional<RiskFeatureSnapshot> findByEvaluationKeyAndFeatureSetReference(String evaluationKey,
                                                                           String featureSetReference);

    /**
     * Reads every snapshot taken for one evaluation.
     *
     * <pre>{@code
     * SELECT * FROM risk_feature_snapshots WHERE evaluation_key = :evaluationKey
     * }</pre>
     *
     * @param evaluationKey evaluation key
     * @return possibly empty list
     */
    List<RiskFeatureSnapshot> findByEvaluationKey(String evaluationKey);
}
