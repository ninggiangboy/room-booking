package dev.ngb.backend.discovery.internal.repository.ranking;

import dev.ngb.backend.discovery.internal.model.ranking.RankingExposure;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Writes what was shown, and reads it back for evaluation and diagnostics.
 *
 * <p>These rows are append-only. They are the only durable record of the position a listing was given,
 * without which every conversion rate derived from the event stream is confounded by the ranking that
 * produced it.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code ranking_exposures}.</p>
 */
public interface RankingExposureRepository extends ListCrudRepository<RankingExposure, UUID> {

    /**
     * Lists what one search actually showed, in order.
     *
     * @param searchRequestId the search
     * @return possibly empty list, best position first
     */
    List<RankingExposure> findBySearchRequestIdOrderByPosition(UUID searchRequestId);

    /**
     * Counts exploration impressions one listing has taken from one window.
     *
     * <pre>{@code
     * SELECT count(*) FROM ranking_exposures
     * WHERE exploration_budget_window_id = :windowId AND listing_id = :listingId
     * }</pre>
     *
     * <p>The per-listing cap is also enforced by trigger, so this read informs the decision rather than
     * guarding it.</p>
     *
     * @param windowId the exploration window
     * @param listingId the listing
     * @return how many exploration impressions it has taken
     */
    @Query("""
            SELECT count(*) FROM ranking_exposures
            WHERE exploration_budget_window_id = :windowId AND listing_id = :listingId
            """)
    long countExplorationImpressions(@Param("windowId") UUID windowId,
            @Param("listingId") UUID listingId);

    /**
     * Lists exposures of one listing within a window, for position-bias estimation.
     *
     * <pre>{@code
     * SELECT * FROM ranking_exposures
     * WHERE listing_id = :listingId AND occurred_at >= :from AND occurred_at < :to
     * ORDER BY occurred_at
     * }</pre>
     *
     * @param listingId the listing
     * @param from start of the window, inclusive
     * @param to end of the window, exclusive
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM ranking_exposures
            WHERE listing_id = :listingId AND occurred_at >= :from AND occurred_at < :to
            ORDER BY occurred_at
            """)
    List<RankingExposure> findForListing(@Param("listingId") UUID listingId,
            @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Deletes exposure records past their retention deadline.
     *
     * <pre>{@code
     * DELETE FROM ranking_exposures WHERE expires_at <= :at
     * }</pre>
     *
     * @param at instant to compare against
     * @return how many rows were removed
     */
    @Query("DELETE FROM ranking_exposures WHERE expires_at <= :at")
    int deleteExpired(@Param("at") Instant at);
}
