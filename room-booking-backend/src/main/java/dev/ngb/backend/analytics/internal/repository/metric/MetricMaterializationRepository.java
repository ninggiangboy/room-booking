package dev.ngb.backend.analytics.internal.repository.metric;

import dev.ngb.backend.analytics.internal.model.metric.MetricMaterialization;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.analytics.internal.model.metric.MetricMaterialization;


/**
 * Reads the computed values of a metric.
 *
 * <p>Reports read the current value for a slice and window. The history stays, because a figure
 * somebody already quoted has to remain findable after a restatement supersedes it.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code metric_materializations}.</p>
 */
public interface MetricMaterializationRepository extends ListCrudRepository<MetricMaterialization, UUID> {

    /**
     * Finds the published value for one metric, slice and window.
     *
     * <pre>{@code
     * SELECT * FROM metric_materializations
     * WHERE metric_definition_id = :metricDefinitionId AND slice_digest = :sliceDigest
     *   AND window_start = :windowStart AND window_end = :windowEnd
     *   AND publication_state = 'CURRENT'
     * }</pre>
     *
     * @param metricDefinitionId the metric version
     * @param sliceDigest digest of the slice dimensions
     * @param windowStart start of the window
     * @param windowEnd end of the window
     * @return the published value, when one stands
     */
    @Query("""
            SELECT * FROM metric_materializations
            WHERE metric_definition_id = :metricDefinitionId AND slice_digest = :sliceDigest
              AND window_start = :windowStart AND window_end = :windowEnd
              AND publication_state = 'CURRENT'
            """)
    Optional<MetricMaterialization> findCurrent(
            @Param("metricDefinitionId") UUID metricDefinitionId,
            @Param("sliceDigest") String sliceDigest, @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd);

    /**
     * Lists the published series for one metric and slice.
     *
     * <pre>{@code
     * SELECT * FROM metric_materializations
     * WHERE metric_definition_id = :metricDefinitionId AND slice_digest = :sliceDigest
     *   AND publication_state = 'CURRENT' AND window_start >= :since
     * ORDER BY window_start
     * }</pre>
     *
     * @param metricDefinitionId the metric version
     * @param sliceDigest digest of the slice dimensions
     * @param since earliest window to include
     * @return possibly empty list, oldest window first
     */
    @Query("""
            SELECT * FROM metric_materializations
            WHERE metric_definition_id = :metricDefinitionId AND slice_digest = :sliceDigest
              AND publication_state = 'CURRENT' AND window_start >= :since
            ORDER BY window_start
            """)
    List<MetricMaterialization> findSeries(@Param("metricDefinitionId") UUID metricDefinitionId,
            @Param("sliceDigest") String sliceDigest, @Param("since") Instant since);

    /**
     * Lists the values one run produced.
     *
     * @param pipelineRunId the run
     * @return possibly empty list of values
     */
    List<MetricMaterialization> findByPipelineRunId(UUID pipelineRunId);

    /**
     * Lists the chain of values that superseded one figure.
     *
     * @param restatesId the value that was replaced
     * @return possibly empty list of replacements
     */
    List<MetricMaterialization> findByRestatesId(UUID restatesId);
}
