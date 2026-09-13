package dev.ngb.backend.repository;

import dev.ngb.backend.model.ModelEvaluationRun;
import dev.ngb.backend.model.ModelEvaluationKind;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads the evidence a model version was compared against something.
 *
 * <p>Append-only. A promotion gate reads these rather than a summary, because the interesting
 * question is which baselines were used and whether any of them failed.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code model_evaluation_runs}.</p>
 */
public interface ModelEvaluationRunRepository extends ListCrudRepository<ModelEvaluationRun, UUID> {

    /**
     * Lists the evaluations of one version, newest first.
     *
     * @param modelVersionId the version
     * @return possibly empty list, most recently run first
     */
    List<ModelEvaluationRun> findByModelVersionIdOrderByRunAtDesc(UUID modelVersionId);

    /**
     * Lists one kind of evaluation for a version.
     *
     * @param modelVersionId the version
     * @param evaluationKind what was measured
     * @return possibly empty list
     */
    List<ModelEvaluationRun> findByModelVersionIdAndEvaluationKind(UUID modelVersionId,
            ModelEvaluationKind evaluationKind);

    /**
     * Lists the evaluations of one version that did not fail, which is the evidence a promotion
     * is allowed to rest on.
     *
     * <pre>{@code
     * SELECT * FROM model_evaluation_runs
     * WHERE model_version_id = :modelVersionId AND result <> 'FAIL'
     * ORDER BY run_at DESC
     * }</pre>
     *
     * @param modelVersionId the version
     * @return possibly empty list, most recently run first
     */
    @Query("""
            SELECT * FROM model_evaluation_runs
            WHERE model_version_id = :modelVersionId AND result <> 'FAIL'
            ORDER BY run_at DESC
            """)
    List<ModelEvaluationRun> findPassing(@Param("modelVersionId") UUID modelVersionId);

    /**
     * Lists the evaluations where the candidate scored worse than its baseline, which no
     * aggregate summary is obliged to surface.
     *
     * <pre>{@code
     * SELECT * FROM model_evaluation_runs
     * WHERE model_version_id = :modelVersionId
     *   AND baseline_metric_value IS NOT NULL
     *   AND primary_metric_value < baseline_metric_value
     * ORDER BY run_at DESC
     * }</pre>
     *
     * @param modelVersionId the version
     * @return possibly empty list, most recently run first
     */
    @Query("""
            SELECT * FROM model_evaluation_runs
            WHERE model_version_id = :modelVersionId
              AND baseline_metric_value IS NOT NULL
              AND primary_metric_value < baseline_metric_value
            ORDER BY run_at DESC
            """)
    List<ModelEvaluationRun> findBelowBaseline(
            @Param("modelVersionId") UUID modelVersionId);
}
