package dev.ngb.backend.analytics.internal.model.metric;

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
import dev.ngb.backend.platform.JsonDocument;

import dev.ngb.backend.analytics.internal.model.DataQualityState;
import dev.ngb.backend.platform.JsonDocument;


/**
 * One computed value of one metric for one slice and window.
 *
 * <p>Append-only except for its publication state. Late data restates by publishing a new row that
 * names the one it replaces, and a number may never claim a cleaner quality standing than the run
 * that produced it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("metric_materializations")
public class MetricMaterialization {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The metric version this value was computed under. */
    private UUID metricDefinitionId;
    /** The run that computed it. */
    private UUID pipelineRunId;
    /** Digest of the slice dimensions, which is what the uniqueness rule is keyed on. */
    private String sliceDigest;
    /** The slice dimensions themselves, kept as an immutable snapshot. */
    private JsonDocument sliceDescriptor;
    /** Start of the window, inclusive. */
    private Instant windowStart;
    /** End of the window, exclusive. */
    private Instant windowEnd;
    /** The value itself. */
    private BigDecimal measuredValue;
    /** The same value in integer minor units, where the metric measures money. */
    private @Nullable Long valueMinor;
    /** ISO 4217 alphabetic code the amounts on this row are denominated in. */
    private @Nullable String currency;
    /** The denominator it was computed over, for a rate. */
    private @Nullable BigDecimal denominatorValue;
    /** How many units the value is based on. */
    private @Nullable Long sampleSize;
    /** Latest data included, which is what makes an open window legible. */
    private Instant dataCutoffAt;
    /** How complete the inputs were, between zero and one. */
    private @Nullable BigDecimal completeness;
    /** Standing of this number, which may never exceed that of its run. */
    private DataQualityState qualityState;
    /** Whether this is the published value, a provisional one, or superseded. */
    private MaterializationPublicationState publicationState;
    /** The value this one replaces. */
    private @Nullable UUID restatesId;
    /** UTC instant it was computed. */
    private Instant materializedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
