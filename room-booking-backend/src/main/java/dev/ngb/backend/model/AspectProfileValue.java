package dev.ngb.backend.model;

import java.math.BigDecimal;
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
import org.springframework.data.relational.core.mapping.Table;

/**
 * One aspect within one profile version.
 *
 * <p>Counts sit beside every posterior, so a strength backed by two mentions can never present itself
 * the way one backed by two hundred does. A class other than insufficient evidence needs mentions
 * behind it, and the sentiment breakdown must add up to the mention count.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("aspect_profile_values")
public class AspectProfileValue {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Profile this value belongs to. */
    private UUID aspectProfileVersionId;
    /** Which aspect. */
    private String aspectCode;
    /** What the value is about. */
    private AspectTarget aspectTarget;
    /** How many mentions were counted. */
    private long mentionCount;
    /** How many were favourable. */
    private long positiveCount;
    /** How many were unfavourable. */
    private long negativeCount;
    /** How many were both. */
    private long mixedCount;
    /** How many passed no judgement. */
    private long neutralCount;
    /** Weighted evidence after recency and confidence weighting. */
    private @Nullable BigDecimal effectiveEvidence;
    /** Estimated standing on this aspect. */
    private @Nullable BigDecimal posteriorMean;
    /** Lower bound of the uncertainty interval. */
    private @Nullable BigDecimal posteriorIntervalLow;
    /** Upper bound of the uncertainty interval. */
    private @Nullable BigDecimal posteriorIntervalHigh;
    /** Value over the recent window. */
    private @Nullable BigDecimal recentValue;
    /** Which way it has moved. */
    private @Nullable TrendDirection trendDirection;
    /** What the evidence amounts to. */
    private AspectEvidenceClass evidenceClass;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Whether this value says anything a host could act on.
     *
     * @return true when the evidence amounts to more than "not enough"
     */
    public boolean isActionable() {
        return evidenceClass != AspectEvidenceClass.INSUFFICIENT;
    }
}
