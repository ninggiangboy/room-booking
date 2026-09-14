package dev.ngb.backend.hostops.internal.repository.metric;

import dev.ngb.backend.hostops.internal.model.metric.HostMetricPublication;
import dev.ngb.backend.platform.GovernedRegistryStatus;
import dev.ngb.backend.hostops.internal.model.metric.HostMetricSurface;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.hostops.internal.model.metric.HostMetricPublication;
import dev.ngb.backend.hostops.internal.model.metric.HostMetricSurface;
import dev.ngb.backend.platform.GovernedRegistryStatus;


/**
 * Reads which metrics a host may see, in what words, and with how much evidence behind them.
 *
 * <p>Every host-facing figure is written against one of these rows, so this is where the question
 * "what was this number claimed to mean when the host acted on it" is answered.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code host_metric_publications}.</p>
 */
public interface HostMetricPublicationRepository extends ListCrudRepository<HostMetricPublication, UUID> {

    /**
     * Finds the publication of one metric on one surface.
     *
     * @param metricDefinitionId the analytics metric
     * @param surface where it is shown
     * @return the publication, when the metric is published there
     */
    Optional<HostMetricPublication> findByMetricDefinitionIdAndSurface(UUID metricDefinitionId,
            HostMetricSurface surface);

    /**
     * Lists the publications on one surface in one lifecycle state.
     *
     * @param surface where they are shown
     * @param status the lifecycle state
     * @return possibly empty list
     */
    List<HostMetricPublication> findBySurfaceAndStatus(HostMetricSurface surface,
            GovernedRegistryStatus status);

    /**
     * Lists the metrics that may be compared across hosts, which is the set a benchmark run is
     * allowed to draw from.
     *
     * <pre>{@code
     * SELECT * FROM host_metric_publications
     * WHERE benchmarkable AND status IN ('ACTIVE', 'DEPRECATED')
     * ORDER BY surface, host_label
     * }</pre>
     *
     * @return possibly empty list, by surface then label
     */
    @Query("""
            SELECT * FROM host_metric_publications
            WHERE benchmarkable AND status IN ('ACTIVE', 'DEPRECATED')
            ORDER BY surface, host_label
            """)
    List<HostMetricPublication> findBenchmarkable();

    /**
     * Lists the metrics that move a host's ranking or standing, which is the set a fairness
     * review of host-facing measurement has to read.
     *
     * <pre>{@code
     * SELECT * FROM host_metric_publications
     * WHERE (affects_ranking OR affects_standing) AND status = 'ACTIVE'
     * ORDER BY surface, host_label
     * }</pre>
     *
     * @return possibly empty list, by surface then label
     */
    @Query("""
            SELECT * FROM host_metric_publications
            WHERE (affects_ranking OR affects_standing) AND status = 'ACTIVE'
            ORDER BY surface, host_label
            """)
    List<HostMetricPublication> findConsequential();
}
