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
import org.springframework.data.relational.core.mapping.Table;

/**
 * What the platform expects one night to do, with the interval around it.
 * <p>A point estimate with no interval is a guess with a decimal point, and it is exactly the shape
 * fabricated scarcity takes: "you will be eighty per cent full" reads as a fact. The interval must
 * contain its own point estimate and states the probability it was computed at, so a wide interval
 * and a narrow one can be told apart on the screen.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("demand_forecasts")
public class DemandForecast {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The run that produced this forecast. */
    private UUID forecastRunId;
    /** The accommodation type the forecast is about. */
    private UUID accommodationTypeId;
    /** The night being forecast. */
    private LocalDate stayDate;
    /** Whether there was enough evidence to forecast this night at all. */
    private MetricEvidenceState evidenceState;
    /** Predicted share of comparable supply sold for this night. */
    private @Nullable BigDecimal predictedOccupancyPercent;
    /** Bottom of the occupancy interval. */
    private @Nullable BigDecimal occupancyIntervalLow;
    /** Top of the occupancy interval. */
    private @Nullable BigDecimal occupancyIntervalHigh;
    /** Predicted number of bookings for this night. */
    private @Nullable BigDecimal predictedBookings;
    /** Bottom of the booking-count interval. */
    private @Nullable BigDecimal bookingsIntervalLow;
    /** Top of the booking-count interval. */
    private @Nullable BigDecimal bookingsIntervalHigh;
    /**
     * The probability the interval is stated at, so a wide interval and a narrow one can be told
     * apart.
     */
    private @Nullable BigDecimal intervalConfidence;
    /** How far ahead or behind the same point last year the market is pacing, as a percentage. */
    private @Nullable BigDecimal marketPacePercent;
    /** What occupancy actually was on the comparable night last year. */
    private @Nullable BigDecimal priorYearOccupancyPercent;
    /** How much weight the model puts on this forecast. */
    private RecommendationConfidence confidence;
    /** What is driving the forecast, in the words a host is shown. */
    private @Nullable String driverSummary;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
