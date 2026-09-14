package dev.ngb.backend.analytics.internal.model.quality;

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

import dev.ngb.backend.analytics.internal.model.DataQualityState;


/**
 * The outcome of one check against one run.
 *
 * <p>Append-only except for the response the owner records. An unknown result carries no number,
 * because ''we could not measure'' and ''we measured zero'' are different statements, and waiving a
 * failure requires a named owner and a reason.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("data_quality_results")
public class DataQualityResult {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The run this result belongs to. */
    private UUID pipelineRunId;
    /** The check that produced it. */
    private UUID dataQualityCheckId;
    /** What the check found. */
    private DataQualityState status;
    /** What was measured; absent when the check could not be evaluated. */
    private @Nullable BigDecimal observedValue;
    /** What it should have been. */
    private @Nullable BigDecimal expectedValue;
    /** A non-numeric observation, where the check produces one. */
    private @Nullable String observedText;
    /** How many rows failed. */
    private @Nullable Long failedRowCount;
    /** Where a bounded sample of the failing rows lives. */
    private @Nullable String sampleReference;
    /** UTC instant the check ran. */
    private Instant evaluatedAt;
    /** How the owner responded. */
    private QualityOwnerAction ownerAction;
    /** Who responded; required for anything but no response. */
    private @Nullable String actionActor;
    /** Why, which is what makes a waiver answerable later. */
    private @Nullable String actionReason;
    /** UTC instant they responded. */
    private @Nullable Instant actedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
