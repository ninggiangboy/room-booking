package dev.ngb.backend.hostops.internal.repository.forecast;

import dev.ngb.backend.hostops.internal.model.forecast.DemandForecastRun;
import dev.ngb.backend.hostops.internal.model.forecast.DemandForecastRunState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the passes of the demand model over a market.
 *
 * <p>A forecast belongs to a run, and a run that has not succeeded may not be shown to a host, so
 * this is the first thing a reader of forecasts has to check.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code demand_forecast_runs}.</p>
 */
public interface DemandForecastRunRepository extends ListCrudRepository<DemandForecastRun, UUID> {

    /**
     * Finds the run whose forecasts may currently be shown for one market: the newest that
     * succeeded and has not been superseded.
     *
     * <pre>{@code
     * SELECT * FROM demand_forecast_runs
     * WHERE market_code = :marketCode AND run_state = 'SUCCEEDED'
     * ORDER BY generated_at DESC
     * LIMIT 1
     * }</pre>
     *
     * @param marketCode the market
     * @return the servable run, when one has succeeded
     */
    @Query("""
            SELECT * FROM demand_forecast_runs
            WHERE market_code = :marketCode AND run_state = 'SUCCEEDED'
            ORDER BY generated_at DESC
            LIMIT 1
            """)
    Optional<DemandForecastRun> findServable(@Param("marketCode") String marketCode);

    /**
     * Lists the runs of one market in one state, newest first.
     *
     * @param marketCode the market
     * @param runState the state
     * @return possibly empty list, most recently generated first
     */
    List<DemandForecastRun> findByMarketCodeAndRunStateOrderByGeneratedAtDesc(String marketCode,
            DemandForecastRunState runState);

    /**
     * Lists the runs whose measured backtest error is worse than a bound, which is what a review
     * of whether the forecasts should still be shown at all has to read.
     *
     * <pre>{@code
     * SELECT * FROM demand_forecast_runs
     * WHERE run_state = 'SUCCEEDED' AND backtest_error_percent > :bound
     * ORDER BY backtest_error_percent DESC
     * }</pre>
     *
     * @param bound the error percentage above which a run is worth reviewing
     * @return possibly empty list, worst first
     */
    @Query("""
            SELECT * FROM demand_forecast_runs
            WHERE run_state = 'SUCCEEDED' AND backtest_error_percent > :bound
            ORDER BY backtest_error_percent DESC
            """)
    List<DemandForecastRun> findInaccurate(@Param("bound") BigDecimal bound);
}
