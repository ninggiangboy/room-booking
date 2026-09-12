package dev.ngb.backend.repository;

import dev.ngb.backend.model.PriceRecommendation;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reads the optimizer's proposals.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code price_recommendations}.</p>
 */
public interface PriceRecommendationRepository extends ListCrudRepository<PriceRecommendation, UUID> {

    /**
     * Returns the live proposals for a calendar window.
     *
     * <pre>{@code
     * SELECT *
     * FROM price_recommendations
     * WHERE accommodation_type_id = :accommodationTypeId
     *   AND stay_date >= :from
     *   AND stay_date < :until
     *   AND decision_state = 'PENDING'
     *   AND expires_at > :instant
     * ORDER BY stay_date
     * }</pre>
     *
     * <p>Expired proposals are filtered by the caller's instant rather than by {@code now()}, because
     * a partial index cannot contain a moving clock and a host reviewing a calendar should see one
     * consistent picture rather than one that shifts mid-page.</p>
     *
     * @param accommodationTypeId accommodation type whose calendar is being reviewed
     * @param from first date shown, inclusive
     * @param until first date past the window, exclusive
     * @param instant the request's decision instant
     * @return possibly empty list, earliest night first
     */
    @Query("""
            SELECT *
            FROM price_recommendations
            WHERE accommodation_type_id = :accommodationTypeId
              AND stay_date >= :from
              AND stay_date < :until
              AND decision_state = 'PENDING'
              AND expires_at > :instant
            ORDER BY stay_date
            """)
    List<PriceRecommendation> findPending(
            @Param("accommodationTypeId") UUID accommodationTypeId,
            @Param("from") LocalDate from,
            @Param("until") LocalDate until,
            @Param("instant") Instant instant);

    /**
     * Returns proposals that lapsed without a decision, for the expiry sweep.
     *
     * <pre>{@code
     * SELECT *
     * FROM price_recommendations
     * WHERE decision_state = 'PENDING'
     *   AND expires_at <= :decisionInstant
     * ORDER BY expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Time passing does not change a stored row, so a worker has to transition stale proposals
     * explicitly; until it does they simply stop being offered.</p>
     *
     * @param decisionInstant the sweep's single decision instant
     * @param batchSize maximum number of rows to return
     * @return possibly empty list, most overdue first
     */
    @Query("""
            SELECT *
            FROM price_recommendations
            WHERE decision_state = 'PENDING'
              AND expires_at <= :decisionInstant
            ORDER BY expires_at
            LIMIT :batchSize
            """)
    List<PriceRecommendation> findLapsed(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);
}
