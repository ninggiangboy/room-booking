package dev.ngb.backend.analytics.internal.repository.experiment;

import dev.ngb.backend.analytics.internal.model.experiment.ExperimentMetric;
import dev.ngb.backend.analytics.internal.model.experiment.MetricRole;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

/**
 * Reads the metrics an epoch declared before it started.
 *
 * <p>The analysis reads this rather than choosing metrics itself, which is the whole point: a metric
 * cannot be added to a sealed epoch, so a result cannot be found by looking until something moved.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code experiment_metrics}.</p>
 */
public interface ExperimentMetricRepository extends ListCrudRepository<ExperimentMetric, UUID> {

    /**
     * Lists the metrics declared on one epoch.
     *
     * @param experimentEpochId the epoch
     * @return possibly empty list of declarations
     */
    List<ExperimentMetric> findByExperimentEpochId(UUID experimentEpochId);

    /**
     * Lists the metrics of one role, such as every guardrail.
     *
     * @param experimentEpochId the epoch
     * @param metricRole the role to list
     * @return possibly empty list of declarations
     */
    List<ExperimentMetric> findByExperimentEpochIdAndMetricRole(UUID experimentEpochId,
            MetricRole metricRole);

    /**
     * Lists the epochs that declared one metric, for impact review before retiring it.
     *
     * <pre>{@code
     * SELECT * FROM experiment_metrics
     * WHERE metric_definition_id = :metricDefinitionId
     * }</pre>
     *
     * @param metricDefinitionId the metric version
     * @return possibly empty list of declarations
     */
    @Query("""
            SELECT * FROM experiment_metrics
            WHERE metric_definition_id = :metricDefinitionId
            """)
    List<ExperimentMetric> findDeclaringEpochs(
            @Param("metricDefinitionId") UUID metricDefinitionId);
}
