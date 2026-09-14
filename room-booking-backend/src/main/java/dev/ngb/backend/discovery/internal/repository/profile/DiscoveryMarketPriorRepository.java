package dev.ngb.backend.discovery.internal.repository.profile;

import dev.ngb.backend.discovery.internal.model.profile.DiscoveryMarketPrior;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the priors an unevidenced subject is shrunk towards.
 *
 * <p>The fallback walk is the point: neighbourhood, then locality, then administrative area, then
 * country, then the marketplace-wide prior, recording which level actually answered.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code discovery_market_priors}.</p>
 */
public interface DiscoveryMarketPriorRepository extends ListCrudRepository<DiscoveryMarketPrior, UUID> {

    /**
     * Finds the most specific current prior for a metric among a chain of candidate areas.
     *
     * <pre>{@code
     * SELECT * FROM discovery_market_priors
     * WHERE metric_key = :metricKey
     *   AND status = 'CURRENT'
     *   AND (geo_area_id = ANY (:geoAreaIds) OR scope_level = 'GLOBAL')
     * ORDER BY CASE scope_level
     *              WHEN 'NEIGHBORHOOD' THEN 0 WHEN 'LOCALITY' THEN 1 WHEN 'ADMIN_AREA' THEN 2
     *              WHEN 'COUNTRY' THEN 3 ELSE 4 END
     * LIMIT 1
     * }</pre>
     *
     * <p>Callers must record the {@code scopeLevel} of whatever comes back, because "we used the country
     * prior" and "this neighbourhood is like this" are different claims.</p>
     *
     * @param metricKey the metric
     * @param geoAreaIds the area and its ancestors, most specific first
     * @return the most specific prior available, which may be the global one
     */
    @Query("""
            SELECT * FROM discovery_market_priors
            WHERE metric_key = :metricKey
              AND status = 'CURRENT'
              AND (geo_area_id = ANY (:geoAreaIds) OR scope_level = 'GLOBAL')
            ORDER BY CASE scope_level
                         WHEN 'NEIGHBORHOOD' THEN 0 WHEN 'LOCALITY' THEN 1 WHEN 'ADMIN_AREA' THEN 2
                         WHEN 'COUNTRY' THEN 3 ELSE 4 END
            LIMIT 1
            """)
    Optional<DiscoveryMarketPrior> findMostSpecific(@Param("metricKey") String metricKey,
            @Param("geoAreaIds") UUID[] geoAreaIds);

    /**
     * Lists current priors due for recomputation.
     *
     * <pre>{@code
     * SELECT * FROM discovery_market_priors
     * WHERE status = 'CURRENT' AND expires_at <= :at
     * ORDER BY expires_at
     * }</pre>
     *
     * @param at instant to compare against
     * @return possibly empty list, most stale first
     */
    @Query("""
            SELECT * FROM discovery_market_priors
            WHERE status = 'CURRENT' AND expires_at <= :at
            ORDER BY expires_at
            """)
    List<DiscoveryMarketPrior> findStale(@Param("at") Instant at);
}
