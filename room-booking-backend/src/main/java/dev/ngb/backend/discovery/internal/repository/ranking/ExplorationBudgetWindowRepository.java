package dev.ngb.backend.discovery.internal.repository.ranking;

import dev.ngb.backend.discovery.internal.model.ranking.ExplorationBudgetWindow;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and claims the budget bounded exploration is spent from.
 *
 * <p>Consumption is incremented by a trigger as each exploration exposure is written, so callers read
 * these rows to decide whether to explore, not to keep the count.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code exploration_budget_windows}.</p>
 */
public interface ExplorationBudgetWindowRepository extends ListCrudRepository<ExplorationBudgetWindow, UUID> {

    /**
     * Finds the open window covering an instant for a market and area.
     *
     * <pre>{@code
     * SELECT * FROM exploration_budget_windows
     * WHERE state = 'OPEN'
     *   AND (market_id = :marketId OR market_id IS NULL)
     *   AND (geo_area_id = :geoAreaId OR geo_area_id IS NULL)
     *   AND opens_at <= :at AND closes_at > :at
     * ORDER BY geo_area_id NULLS LAST, market_id NULLS LAST
     * LIMIT 1
     * }</pre>
     *
     * <p>The most specific window wins, which is why the ordering puts nulls last.</p>
     *
     * @param marketId market, or null for the global window
     * @param geoAreaId area, or null for the market-wide window
     * @param at instant to resolve at
     * @return the window that would fund an exploration impression, when one is open
     */
    @Query("""
            SELECT * FROM exploration_budget_windows
            WHERE state = 'OPEN'
              AND (market_id = :marketId OR market_id IS NULL)
              AND (geo_area_id = :geoAreaId OR geo_area_id IS NULL)
              AND opens_at <= :at AND closes_at > :at
            ORDER BY geo_area_id NULLS LAST, market_id NULLS LAST
            LIMIT 1
            """)
    Optional<ExplorationBudgetWindow> findOpen(@Param("marketId") @Nullable UUID marketId,
            @Param("geoAreaId") @Nullable UUID geoAreaId, @Param("at") Instant at);

    /**
     * Locks one window for the caller's transaction.
     *
     * <pre>{@code
     * SELECT * FROM exploration_budget_windows WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Transaction required. Take this before a batch of exploration decisions so two concurrent
     * searches cannot both believe the last impression of the allocation is theirs.</p>
     *
     * @param id the window
     * @return the locked window, when it exists
     */
    @Query("SELECT * FROM exploration_budget_windows WHERE id = :id FOR UPDATE")
    Optional<ExplorationBudgetWindow> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Lists windows whose scheduled close has passed.
     *
     * <pre>{@code
     * SELECT * FROM exploration_budget_windows
     * WHERE state = 'OPEN' AND closes_at <= :at
     * ORDER BY closes_at
     * }</pre>
     *
     * @param at instant to compare against
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT * FROM exploration_budget_windows
            WHERE state = 'OPEN' AND closes_at <= :at
            ORDER BY closes_at
            """)
    List<ExplorationBudgetWindow> findDue(@Param("at") Instant at);
}
