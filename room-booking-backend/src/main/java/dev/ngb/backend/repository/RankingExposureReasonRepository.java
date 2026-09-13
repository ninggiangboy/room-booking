package dev.ngb.backend.repository;

import dev.ngb.backend.model.RankingExposureReason;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Writes the reasons shown for one result and reads them back.
 *
 * <p>Every row is checked against the approved vocabulary and its thresholds as it is written, so a
 * reason that reached the database is a reason that cleared its bar at the moment it was shown.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code ranking_exposure_reasons}.</p>
 */
public interface RankingExposureReasonRepository extends ListCrudRepository<RankingExposureReason, UUID> {

    /**
     * Lists the reasons shown for one result, in display order.
     *
     * @param rankingExposureId the result
     * @return possibly empty list, as shown
     */
    List<RankingExposureReason> findByRankingExposureIdOrderByDisplayRank(UUID rankingExposureId);

    /**
     * Lists the reasons shown across one whole search, for the diagnostic view.
     *
     * <pre>{@code
     * SELECT r.* FROM ranking_exposure_reasons r
     * JOIN ranking_exposures e ON e.id = r.ranking_exposure_id
     * WHERE e.search_request_id = :searchRequestId
     * ORDER BY e.position, r.display_rank
     * }</pre>
     *
     * @param searchRequestId the search
     * @return possibly empty list, in the order a guest saw them
     */
    @Query("""
            SELECT r.* FROM ranking_exposure_reasons r
            JOIN ranking_exposures e ON e.id = r.ranking_exposure_id
            WHERE e.search_request_id = :searchRequestId
            ORDER BY e.position, r.display_rank
            """)
    List<RankingExposureReason> findForSearch(@Param("searchRequestId") UUID searchRequestId);
}
