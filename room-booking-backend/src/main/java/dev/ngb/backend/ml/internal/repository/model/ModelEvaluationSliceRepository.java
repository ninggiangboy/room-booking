package dev.ngb.backend.ml.internal.repository.model;

import dev.ngb.backend.ml.internal.model.model.ModelEvaluationSlice;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

/**
 * Reads what a model did to each measured group.
 *
 * <p>The point of reading slices separately is that an excellent aggregate can sit on top of one
 * market, one language or one class of host carrying every error.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code model_evaluation_slices}.</p>
 */
public interface ModelEvaluationSliceRepository extends ListCrudRepository<ModelEvaluationSlice, UUID> {

    /**
     * Lists the slices of one evaluation.
     *
     * @param modelEvaluationRunId the evaluation
     * @return possibly empty list
     */
    List<ModelEvaluationSlice> findByModelEvaluationRunId(UUID modelEvaluationRunId);

    /**
     * Lists the slices of one evaluation that breached their threshold.
     *
     * <pre>{@code
     * SELECT * FROM model_evaluation_slices
     * WHERE model_evaluation_run_id = :modelEvaluationRunId AND breached
     * ORDER BY harm_class, slice_dimension, slice_value
     * }</pre>
     *
     * @param modelEvaluationRunId the evaluation
     * @return possibly empty list, by harm class then slice
     */
    @Query("""
            SELECT * FROM model_evaluation_slices
            WHERE model_evaluation_run_id = :modelEvaluationRunId AND breached
            ORDER BY harm_class, slice_dimension, slice_value
            """)
    List<ModelEvaluationSlice> findBreached(
            @Param("modelEvaluationRunId") UUID modelEvaluationRunId);

    /**
     * Lists the fairness slices recorded across every evaluation of one model version, which is
     * what an approval reads rather than the aggregate the candidate was judged on.
     *
     * <pre>{@code
     * SELECT s.* FROM model_evaluation_slices s
     * JOIN model_evaluation_runs r ON r.id = s.model_evaluation_run_id
     * WHERE r.model_version_id = :modelVersionId AND r.evaluation_kind = 'FAIRNESS'
     * ORDER BY s.harm_class, s.slice_dimension, s.slice_value
     * }</pre>
     *
     * @param modelVersionId the version
     * @return possibly empty list, by harm class then slice
     */
    @Query("""
            SELECT s.* FROM model_evaluation_slices s
            JOIN model_evaluation_runs r ON r.id = s.model_evaluation_run_id
            WHERE r.model_version_id = :modelVersionId AND r.evaluation_kind = 'FAIRNESS'
            ORDER BY s.harm_class, s.slice_dimension, s.slice_value
            """)
    List<ModelEvaluationSlice> findFairnessSlices(
            @Param("modelVersionId") UUID modelVersionId);
}
