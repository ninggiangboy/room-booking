package dev.ngb.backend.hostops.internal.repository.forecast;

import dev.ngb.backend.hostops.internal.model.forecast.DemandForecast;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what the platform expects one night to do.
 *
 * <p>Every row carries the interval around its point estimate, and a reader that shows the point
 * without the interval has turned a forecast into a claim.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code demand_forecasts}.</p>
 */
public interface DemandForecastRepository extends ListCrudRepository<DemandForecast, UUID> {

    /**
     * Lists one run's forecasts for one accommodation type, by night.
     *
     * @param forecastRunId the run
     * @param accommodationTypeId the accommodation type
     * @return possibly empty list, earliest night first
     */
    List<DemandForecast> findByForecastRunIdAndAccommodationTypeIdOrderByStayDate(
            UUID forecastRunId, UUID accommodationTypeId);

    /**
     * Finds one night in one run.
     *
     * @param forecastRunId the run
     * @param accommodationTypeId the accommodation type
     * @param stayDate the night
     * @return the forecast, when the run produced one for that night
     */
    Optional<DemandForecast> findByForecastRunIdAndAccommodationTypeIdAndStayDate(
            UUID forecastRunId, UUID accommodationTypeId, LocalDate stayDate);

    /**
     * Lists the nights a servable run expects to go soft, which is the set a pricing suggestion
     * is worth computing over.
     *
     * <pre>{@code
     * SELECT f.* FROM demand_forecasts f
     * JOIN demand_forecast_runs r ON r.id = f.forecast_run_id
     * WHERE r.run_state = 'SUCCEEDED'
     *   AND f.accommodation_type_id = :accommodationTypeId
     *   AND f.evidence_state = 'SUFFICIENT'
     *   AND f.predicted_occupancy_percent < :threshold
     * ORDER BY f.stay_date
     * }</pre>
     *
     * @param accommodationTypeId the accommodation type
     * @param threshold the occupancy percentage below which a night is worth acting on
     * @return possibly empty list, earliest night first
     */
    @Query("""
            SELECT f.* FROM demand_forecasts f
            JOIN demand_forecast_runs r ON r.id = f.forecast_run_id
            WHERE r.run_state = 'SUCCEEDED'
              AND f.accommodation_type_id = :accommodationTypeId
              AND f.evidence_state = 'SUFFICIENT'
              AND f.predicted_occupancy_percent < :threshold
            ORDER BY f.stay_date
            """)
    List<DemandForecast> findSoftNights(
            @Param("accommodationTypeId") UUID accommodationTypeId,
            @Param("threshold") BigDecimal threshold);
}
