package dev.ngb.backend.discovery.internal.repository.ranking;

import dev.ngb.backend.discovery.internal.model.ranking.RankingModelVersion;
import dev.ngb.backend.discovery.internal.model.ranking.RankingModelStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the discovery serving registry.
 *
 * <p>What is live, what is only shadowing, what share of traffic it takes, and what serves when it
 * times out. Migration 031 owns the general model lifecycle; this is the serving contract.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code ranking_model_versions}.</p>
 */
public interface RankingModelVersionRepository extends ListCrudRepository<RankingModelVersion, UUID> {

    /**
     * Finds the model currently serving a market, falling back to the global registration.
     *
     * <pre>{@code
     * SELECT * FROM ranking_model_versions
     * WHERE model_key = :modelKey
     *   AND (market_id = :marketId OR market_id IS NULL)
     *   AND status = 'ACTIVE'
     * ORDER BY market_id NULLS LAST, promoted_at DESC
     * LIMIT 1
     * }</pre>
     *
     * @param modelKey model family
     * @param marketId market, or null for the global fallback
     * @return the serving model, when one is
     */
    @Query("""
            SELECT * FROM ranking_model_versions
            WHERE model_key = :modelKey
              AND (market_id = :marketId OR market_id IS NULL)
              AND status = 'ACTIVE'
            ORDER BY market_id NULLS LAST, promoted_at DESC
            LIMIT 1
            """)
    Optional<RankingModelVersion> findServing(@Param("modelKey") String modelKey,
            @Param("marketId") @Nullable UUID marketId);

    /**
     * Lists models in a given lifecycle state, for the release console.
     *
     * @param status the state to list
     * @return possibly empty list
     */
    List<RankingModelVersion> findByStatus(RankingModelStatus status);

    /**
     * Finds one registered version by its family and label.
     *
     * @param modelKey model family
     * @param modelVersion version label
     * @return the version, when registered
     */
    Optional<RankingModelVersion> findByModelKeyAndModelVersion(String modelKey,
            String modelVersion);
}
