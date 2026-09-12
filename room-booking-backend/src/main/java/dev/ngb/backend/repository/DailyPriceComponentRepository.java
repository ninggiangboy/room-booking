package dev.ngb.backend.repository;

import dev.ngb.backend.model.DailyPriceComponent;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads materialized nightly prices.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code daily_price_components}.</p>
 *
 * <p>This is the read path for search and the calendar. Quoting reads it too, but a quote records the
 * price version it used, so a re-materialization between the search and the quote cannot silently
 * change what the guest was offered.</p>
 */
public interface DailyPriceComponentRepository extends ListCrudRepository<DailyPriceComponent, UUID> {

    /**
     * Returns the current prices for a stay window under one offer.
     *
     * <pre>{@code
     * SELECT *
     * FROM daily_price_components
     * WHERE accommodation_type_id = :accommodationTypeId
     *   AND rate_plan_id = :ratePlanId
     *   AND stay_date >= :checkIn
     *   AND stay_date < :checkOut
     *   AND is_current = true
     * ORDER BY stay_date
     * }</pre>
     *
     * <p>The window is half-open, matching how stays are stored: the checkout date is not a night and
     * is not priced. A short result means some nights have no materialized price, which is a refusal
     * to quote rather than a reason to substitute one.</p>
     *
     * @param accommodationTypeId accommodation type being priced
     * @param ratePlanId offer being priced under
     * @param checkIn first night, inclusive
     * @param checkOut departure date, exclusive
     * @return possibly empty list, earliest night first
     */
    @Query("""
            SELECT *
            FROM daily_price_components
            WHERE accommodation_type_id = :accommodationTypeId
              AND rate_plan_id = :ratePlanId
              AND stay_date >= :checkIn
              AND stay_date < :checkOut
              AND is_current = true
            ORDER BY stay_date
            """)
    List<DailyPriceComponent> findCurrentForStay(
            @Param("accommodationTypeId") UUID accommodationTypeId,
            @Param("ratePlanId") UUID ratePlanId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut);

    /**
     * Returns the current price for one night under one offer.
     *
     * <p>Spring derives
     * {@code WHERE accommodation_type_id = ? AND rate_plan_id = ? AND stay_date = ? AND is_current = true}.
     * At most one row can match: a partial unique index allows only one current price per night and
     * offer, so a guest never sees a price that depends on which row was read first.</p>
     *
     * @param accommodationTypeId accommodation type being priced
     * @param ratePlanId offer being priced under
     * @param stayDate the night
     * @return the current price, or empty when the night has not been priced
     */
    Optional<DailyPriceComponent> findByAccommodationTypeIdAndRatePlanIdAndStayDateAndIsCurrentTrue(
            UUID accommodationTypeId, UUID ratePlanId, LocalDate stayDate);
}
