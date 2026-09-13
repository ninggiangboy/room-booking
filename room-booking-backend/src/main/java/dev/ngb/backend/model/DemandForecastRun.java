package dev.ngb.backend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One pass of the demand model over one market, with the horizon it claims to see and the backtest
 * error that horizon was measured at.
 * <p>Forecasts are written while the run is RUNNING and the run is then closed against its own row
 * count, so a truncated run cannot report a complete one. A finished run is superseded by a later
 * one rather than reopened, which is what every fresh overnight run does to the one before it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("demand_forecast_runs")
public class DemandForecastRun {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** ISO 3166-1 alpha-2 market the run forecasts. */
    private String marketCode;
    /** The geographic area the run is confined to, where it is narrower than the market. */
    private @Nullable UUID geoAreaId;
    /** What the run reasons from: search demand, booking pace, a blend, or a seasonal baseline. */
    private DemandForecastBasis forecastBasis;
    /** The model version that produced the forecasts, where a model produced them. */
    private @Nullable UUID modelVersionId;
    /** How far ahead the run claims to see, in days. */
    private int horizonDays;
    /** First stay date the run forecasts. */
    private LocalDate coversFrom;
    /** Day after the last stay date the run forecasts. */
    private LocalDate coversUntil;
    /** UTC instant up to which input features were complete when the run started. */
    private Instant featureWatermark;
    /** Where the run stands. */
    private DemandForecastRunState runState;
    /**
     * How many forecasts the run claims to have produced, checked against the rows when it closes.
     */
    private int forecastCount;
    /** Reference to the backtest that measured this configurations error. */
    private @Nullable String backtestReference;
    /**
     * Mean absolute percentage error the backtest measured, so the runs own accuracy is on the row.
     */
    private @Nullable BigDecimal backtestErrorPercent;
    /** Why the run failed. */
    private @Nullable String failureReason;
    /** The later run that replaced this one. */
    private @Nullable UUID supersededByRunId;
    /** UTC instant the run was started. */
    private Instant generatedAt;
    /** UTC instant the run finished, whether it succeeded or failed. */
    private @Nullable Instant completedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;
}
