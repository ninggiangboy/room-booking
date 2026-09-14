package dev.ngb.backend.hostops.internal.model.metric;

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

import dev.ngb.backend.hostops.internal.model.MetricEvidenceState;


/**
 * One computed figure about one listing, accommodation type, property or host, over one period.
 * <p>It carries the counts that produced it and the watermark it was cut at, so a host who disputes
 * their own number can be shown the arithmetic. Where the evidence fell short of the publication's
 * floor the row records that instead of rounding noise into a number. Append-only: a correction is
 * a later row, because a figure restated in place makes last month's decision look as though it
 * were taken on this month's number.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_performance_metrics")
public class HostPerformanceMetric {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The host-facing publication this value was computed for. */
    private UUID publicationId;
    /**
     * What the value is about: one listing, one accommodation type, one property, or the host as a
     * whole.
     */
    private HostMetricSubjectKind subjectKind;
    /** The listing, accommodation type, property or host the value describes. */
    private UUID subjectId;
    /** ISO 3166-1 alpha-2 market the subject trades in. */
    private @Nullable String marketCode;
    /** First civil day the value covers. */
    private LocalDate periodStart;
    /** Day after the last civil day the value covers; the period is half-open. */
    private LocalDate periodEnd;
    /** IANA zone the civil period boundaries were computed in. */
    private String periodTimeZone;
    /** Whether there was enough evidence to produce a number, and if not, why there is none. */
    private MetricEvidenceState evidenceState;
    /** The value itself, present only when the evidence was sufficient. */
    private @Nullable BigDecimal measuredValue;
    /** The value in integer minor units, for metrics that measure money. */
    private @Nullable Long valueMinor;
    /** ISO 4217 alphabetic code the minor-unit value is denominated in. */
    private @Nullable String currency;
    /**
     * How many events were in the numerator, so a rate can be checked against the counts that
     * produced it.
     */
    private @Nullable Long numeratorCount;
    /** How many events were in the denominator. */
    private @Nullable Long denominatorCount;
    /**
     * How many observations went into the value, which is what the publications evidence floor is
     * checked against.
     */
    private long observationCount;
    /** The same metric over the comparison period, where one is shown beside it. */
    private @Nullable BigDecimal comparisonValue;
    /** First civil day of the comparison period. */
    private @Nullable LocalDate comparisonPeriodStart;
    /** Day after the last civil day of the comparison period. */
    private @Nullable LocalDate comparisonPeriodEnd;
    /**
     * The analytics materialization this value was read from, where it came from the metric
     * pipeline rather than a direct computation.
     */
    private @Nullable UUID metricMaterializationId;
    /** UTC instant up to which input data was complete when the value was computed. */
    private Instant inputWatermark;
    /** UTC instant the value was computed. */
    private Instant computedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
