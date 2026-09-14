package dev.ngb.backend.discovery.internal.repository.ranking;

import dev.ngb.backend.discovery.internal.model.ranking.RankingEpoch;
import dev.ngb.backend.discovery.internal.model.ranking.RankingEpochState;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.discovery.internal.model.ranking.RankingEpoch;
import dev.ngb.backend.discovery.internal.model.ranking.RankingEpochState;


/**
 * Reads the ranking epoch a cursor is bound to.
 *
 * <p>A cursor naming a closed or invalidated epoch is stale, and the caller must start a new search
 * rather than paginate into an ordering that no longer exists.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code ranking_epochs}.</p>
 */
public interface RankingEpochRepository extends ListCrudRepository<RankingEpoch, UUID> {

    /**
     * Finds the open epoch for a market.
     *
     * @param marketId the market, or null for the global epoch
     * @param state normally {@code OPEN}
     * @return the open epoch, when one is
     */
    Optional<RankingEpoch> findByMarketIdAndState(@Nullable UUID marketId, RankingEpochState state);

    /**
     * Finds one epoch by the key a cursor carries.
     *
     * @param epochKey key from the cursor
     * @return the epoch, when it exists
     */
    Optional<RankingEpoch> findByEpochKey(String epochKey);

    /**
     * Lists epochs past their scheduled close, for the rotation worker.
     *
     * <pre>{@code
     * SELECT * FROM ranking_epochs
     * WHERE state = 'OPEN' AND closes_at <= :at
     * ORDER BY closes_at
     * }</pre>
     *
     * @param at instant to compare against
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT * FROM ranking_epochs
            WHERE state = 'OPEN' AND closes_at <= :at
            ORDER BY closes_at
            """)
    List<RankingEpoch> findDue(@Param("at") Instant at);
}
