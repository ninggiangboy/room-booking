package dev.ngb.backend.trust.internal.repository.label;

import dev.ngb.backend.trust.internal.model.label.RiskLabel;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.trust.internal.model.label.RiskLabel;


/**
 * Reads adjudicated outcomes.
 *
 * <p>The training query applies the maturation window in the database rather than in a pipeline, so a
 * label cannot be handed to a model before the outcome behind it was observed.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_labels}.</p>
 */
public interface RiskLabelRepository extends ListCrudRepository<RiskLabel, UUID> {

    /**
     * Reads the live label for one subject and action.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_labels
     * WHERE risk_label_taxonomy_version_id = :riskLabelTaxonomyVersionId
     *   AND subject_id = :subjectId
     *   AND protected_action IS NOT DISTINCT FROM :protectedAction
     *   AND superseded_by_label_id IS NULL
     * }</pre>
     *
     * <p>Matches {@code uk_risk_labels_live}, so at most one row can come back: a reversal supersedes
     * rather than coexisting.</p>
     *
     * @param riskLabelTaxonomyVersionId vocabulary version
     * @param subjectId subject
     * @param protectedAction action, or null
     * @return the live label, when there is one
     */
    @Query("""
            SELECT *
            FROM risk_labels
            WHERE risk_label_taxonomy_version_id = :riskLabelTaxonomyVersionId
              AND subject_id = :subjectId
              AND protected_action IS NOT DISTINCT FROM :protectedAction
              AND superseded_by_label_id IS NULL
            """)
    Optional<RiskLabel> findLive(@Param("riskLabelTaxonomyVersionId") UUID riskLabelTaxonomyVersionId,
                                 @Param("subjectId") UUID subjectId,
                                 @Param("protectedAction") String protectedAction);

    /**
     * Reads the labels a training run may use.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_labels
     * WHERE risk_label_taxonomy_version_id = :riskLabelTaxonomyVersionId
     *   AND training_eligible = TRUE
     *   AND available_for_training_at <= :asOf
     *   AND superseded_by_label_id IS NULL
     * ORDER BY available_for_training_at
     * }</pre>
     *
     * <p>Every row is confirmed and carries evidence, because the check constraints refuse training
     * eligibility otherwise, and none of them came from the platform's own decision.</p>
     *
     * @param riskLabelTaxonomyVersionId vocabulary version
     * @param asOf instant the training run reads as of
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT *
            FROM risk_labels
            WHERE risk_label_taxonomy_version_id = :riskLabelTaxonomyVersionId
              AND training_eligible = TRUE
              AND available_for_training_at <= :asOf
              AND superseded_by_label_id IS NULL
            ORDER BY available_for_training_at
            """)
    List<RiskLabel> findTrainable(@Param("riskLabelTaxonomyVersionId") UUID riskLabelTaxonomyVersionId,
                                  @Param("asOf") Instant asOf);

    /**
     * Reads one subject's label history.
     *
     * <pre>{@code
     * SELECT * FROM risk_labels WHERE subject_id = :subjectId ORDER BY observed_at DESC
     * }</pre>
     *
     * @param subjectId subject
     * @return possibly empty list, most recent first
     */
    List<RiskLabel> findBySubjectIdOrderByObservedAtDesc(UUID subjectId);
}
