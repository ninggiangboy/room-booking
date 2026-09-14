package dev.ngb.backend.analytics.internal.repository.experiment;

import dev.ngb.backend.analytics.internal.model.experiment.ExperimentAnalysisEstimate;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

/**
 * Reads the effect estimates one analysis produced.
 *
 * <p>Append-only. A confirmatory estimate answers a question that was written down before the data
 * existed; an exploratory one is a question the data suggested, and the two are kept apart here so
 * a readout cannot quietly promote the second to the first.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code experiment_analysis_estimates}.</p>
 */
public interface ExperimentAnalysisEstimateRepository extends ListCrudRepository<ExperimentAnalysisEstimate, UUID> {

    /**
     * Lists the estimates of one analysis.
     *
     * @param experimentAnalysisRunId the analysis
     * @return possibly empty list of estimates
     */
    List<ExperimentAnalysisEstimate> findByExperimentAnalysisRunId(UUID experimentAnalysisRunId);

    /**
     * Lists the overall estimates of one analysis, leaving the heterogeneity slices aside.
     *
     * @param experimentAnalysisRunId the analysis
     * @return possibly empty list of unsliced estimates
     */
    List<ExperimentAnalysisEstimate> findByExperimentAnalysisRunIdAndSliceKeyIsNull(
            UUID experimentAnalysisRunId);

    /**
     * Lists the estimates that crossed a guardrail.
     *
     * @param experimentAnalysisRunId the analysis
     * @param guardrailBreached normally {@code true}
     * @return possibly empty list of breaches
     */
    List<ExperimentAnalysisEstimate> findByExperimentAnalysisRunIdAndGuardrailBreached(
            UUID experimentAnalysisRunId, boolean guardrailBreached);

    /**
     * Lists the estimates for one declared metric across every analysis of its epoch.
     *
     * <pre>{@code
     * SELECT e.* FROM experiment_analysis_estimates e
     * JOIN experiment_analysis_runs r ON r.id = e.experiment_analysis_run_id
     * WHERE e.experiment_metric_id = :experimentMetricId AND e.slice_key IS NULL
     * ORDER BY r.run_at
     * }</pre>
     *
     * @param experimentMetricId the declared metric
     * @return possibly empty list, oldest analysis first
     */
    @Query("""
            SELECT e.* FROM experiment_analysis_estimates e
            JOIN experiment_analysis_runs r ON r.id = e.experiment_analysis_run_id
            WHERE e.experiment_metric_id = :experimentMetricId AND e.slice_key IS NULL
            ORDER BY r.run_at
            """)
    List<ExperimentAnalysisEstimate> findSeries(
            @Param("experimentMetricId") UUID experimentMetricId);
}
