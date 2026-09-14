package dev.ngb.backend.analytics.internal.repository.metric;

import dev.ngb.backend.analytics.internal.model.metric.MetricDefinition;
import dev.ngb.backend.analytics.internal.model.DataContractStatus;
import dev.ngb.backend.analytics.internal.model.metric.MetricFamily;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.analytics.internal.model.DataContractStatus;
import dev.ngb.backend.analytics.internal.model.metric.MetricDefinition;
import dev.ngb.backend.analytics.internal.model.metric.MetricFamily;


/**
 * Reads the governed definition behind a number.
 *
 * <p>A report shows a metric version, not a metric name, because two versions of one name may run in
 * parallel while consumers migrate and their values are not comparable.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code metric_definitions}.</p>
 */
public interface MetricDefinitionRepository extends ListCrudRepository<MetricDefinition, UUID> {

    /**
     * Finds the version consumers read by default.
     *
     * @param metricKey the metric
     * @param status normally {@code CURRENT}
     * @return the current version, when one is published
     */
    Optional<MetricDefinition> findByMetricKeyAndStatus(String metricKey,
            DataContractStatus status);

    /**
     * Finds one exact semantic version.
     *
     * @param metricKey the metric
     * @param semanticVersion the version
     * @return the version, when it is registered
     */
    Optional<MetricDefinition> findByMetricKeyAndSemanticVersion(String metricKey,
            short semanticVersion);

    /**
     * Lists the metrics of one family that are readable today.
     *
     * @param metricFamily the family
     * @param status normally {@code CURRENT}
     * @return possibly empty list, by metric key
     */
    List<MetricDefinition> findByMetricFamilyAndStatusOrderByMetricKeyAsc(MetricFamily metricFamily,
            DataContractStatus status);

    /**
     * Lists the metrics computed from one dataset, for impact review before a schema change.
     *
     * <pre>{@code
     * SELECT * FROM metric_definitions
     * WHERE source_data_product_id = :dataProductId AND status IN ('CURRENT', 'DEPRECATED')
     * ORDER BY metric_key
     * }</pre>
     *
     * @param dataProductId the dataset version
     * @return possibly empty list, by metric key
     */
    @Query("""
            SELECT * FROM metric_definitions
            WHERE source_data_product_id = :dataProductId AND status IN ('CURRENT', 'DEPRECATED')
            ORDER BY metric_key
            """)
    List<MetricDefinition> findDependentOn(@Param("dataProductId") UUID dataProductId);
}
