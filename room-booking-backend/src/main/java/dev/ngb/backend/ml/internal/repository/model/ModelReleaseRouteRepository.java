package dev.ngb.backend.ml.internal.repository.model;

import dev.ngb.backend.ml.internal.model.model.ModelReleaseRoute;
import dev.ngb.backend.ml.internal.model.model.RouteMode;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.ml.internal.model.model.ModelReleaseRoute;
import dev.ngb.backend.ml.internal.model.model.RouteMode;


/**
 * Reads where each model version is live.
 *
 * <p>The route is resolved once per canonical request and recorded on the prediction, so the
 * lookup here is the single point at which a model becomes part of a decision.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code model_release_routes}.</p>
 */
public interface ModelReleaseRouteRepository extends ListCrudRepository<ModelReleaseRoute, UUID> {

    /**
     * Resolves the active route for a consumer, scope and market, preferring a market-specific
     * route over the global one.
     *
     * <pre>{@code
     * SELECT * FROM model_release_routes
     * WHERE consumer = :consumer AND decision_scope = :decisionScope
     *   AND (market_code = :marketCode OR market_code IS NULL)
     *   AND route_mode = 'ACTIVE' AND effective_to IS NULL
     * ORDER BY market_code NULLS LAST
     * LIMIT 1
     * }</pre>
     *
     * @param consumer the service or use case asking
     * @param decisionScope the decision it covers
     * @param marketCode the market the request was made in
     * @return the route to use, when one is active
     */
    @Query("""
            SELECT * FROM model_release_routes
            WHERE consumer = :consumer AND decision_scope = :decisionScope
              AND (market_code = :marketCode OR market_code IS NULL)
              AND route_mode = 'ACTIVE' AND effective_to IS NULL
            ORDER BY market_code NULLS LAST
            LIMIT 1
            """)
    Optional<ModelReleaseRoute> resolveActive(@Param("consumer") String consumer,
            @Param("decisionScope") String decisionScope,
            @Param("marketCode") @Nullable String marketCode);

    /**
     * Lists the routes one version champions, for impact analysis before retirement.
     *
     * @param championModelVersionId the version
     * @return possibly empty list
     */
    List<ModelReleaseRoute> findByChampionModelVersionId(UUID championModelVersionId);

    /**
     * Lists the routes in one mode.
     *
     * @param routeMode how much traffic they carry
     * @return possibly empty list
     */
    List<ModelReleaseRoute> findByRouteMode(RouteMode routeMode);

    /**
     * Lists the routes running a champion/challenger split, each with the epoch it is measured
     * under.
     *
     * <pre>{@code
     * SELECT * FROM model_release_routes
     * WHERE challenger_model_version_id IS NOT NULL AND effective_to IS NULL
     * ORDER BY consumer, decision_scope
     * }</pre>
     *
     * @return possibly empty list, by consumer then scope
     */
    @Query("""
            SELECT * FROM model_release_routes
            WHERE challenger_model_version_id IS NOT NULL AND effective_to IS NULL
            ORDER BY consumer, decision_scope
            """)
    List<ModelReleaseRoute> findSplitting();
}
