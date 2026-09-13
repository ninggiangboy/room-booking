package dev.ngb.backend.model;

import java.time.Instant;
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
 * One governed metric at one semantic version.
 *
 * <p>The key names its own window and version, so that a twenty-eight day confirmed-booking rate
 * cannot be quoted in a slide as though it were the ninety day completed-stay one. Everything a
 * reader needs to interpret the number lives here: population, exclusions, deduplication, bot
 * filter, time basis, zone, maturity horizon and the single dataset it is computed from.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("metric_definitions")
public class MetricDefinition {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /**
     * Stable key, which names its own window and semantic version so two metrics cannot be confused
     * in a report title.
     */
    private String metricKey;
    /** Which semantic version this row defines. */
    private short semanticVersion;
    /** Which part of the marketplace it describes. */
    private MetricFamily metricFamily;
    /** The question it is meant to answer. */
    private String businessQuestion;
    /** What it means, in words, for somebody reading a number. */
    private String humanDefinition;
    /** What is counted. */
    private String numeratorExpression;
    /** What it is counted over; required for a rate. */
    private @Nullable String denominatorExpression;
    /** What the value is measured in. */
    private MeasureUnit measureUnit;
    /** How more than one currency is dealt with; required for money. */
    private @Nullable CurrencyHandling currencyHandling;
    /** The finance-approved rate dataset used for conversion. */
    private @Nullable UUID fxDataProductId;
    /** What one materialized value represents. */
    private String grain;
    /** Who or what is in scope. */
    private String populationExpression;
    /** Who or what is deliberately left out. */
    private @Nullable String exclusionExpression;
    /** How repeated observations of one unit are counted. */
    private String deduplicationRule;
    /** Which versioned bot and internal-traffic filter applies. */
    private String botFilterVersion;
    /** How long the aggregation window is. */
    private int windowDays;
    /** Which clock the window is measured against. */
    private WindowTimeBasis windowTimeBasis;
    /** IANA zone the window boundaries are computed in. */
    private String windowTimeZone;
    /** How an outcome is attributed to a touchpoint, where attribution applies. */
    private @Nullable String attributionRule;
    /** How long before an outcome is safe to count. */
    private int outcomeMaturityDays;
    /** The single governed dataset it is computed from. */
    private UUID sourceDataProductId;
    /** The weakest input standing it will accept. */
    private QualityFloor minimumQualityState;
    /** How much authority the number carries. */
    private MetricAuthorityClass authorityClass;
    /** The one accountable business or domain owner. */
    private String businessOwner;
    /** The one technical steward who maintains it. */
    private String technicalSteward;
    /** Which sensitivity class this row carries. */
    private SensitivityClass sensitivityClass;
    /** What decisions this metric is meant to inform. */
    private String intendedDecisions;
    /** Which version of the query computes it. */
    private String queryVersion;
    /** Worked examples the definition was validated against. */
    private @Nullable String validationExampleReference;
    /** Whether and how published values may be restated. */
    private RestatementPolicy restatementPolicy;
    /** Whether values from the previous version may be compared with these. */
    private boolean comparableWithPrevious;
    /** The metric version this one replaces. */
    private @Nullable UUID supersedesId;
    /** Where this version stands in its lifecycle. */
    private DataContractStatus status;
    /** UTC instant reviewed. */
    private @Nullable Instant reviewedAt;
    /** UTC instant published. */
    private @Nullable Instant publishedAt;
    /** UTC instant deprecated. */
    private @Nullable Instant deprecatedAt;
    /** UTC instant retired. */
    private @Nullable Instant retiredAt;
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
