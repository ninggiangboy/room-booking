package dev.ngb.backend.analytics.internal.model.pipeline;

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

import dev.ngb.backend.analytics.internal.model.DataQualityState;


/**
 * One execution of one transformation over one input watermark.
 *
 * <p>The specification digest, the input watermark and the partition together identify the run, so
 * a retry after a crash returns the same row rather than a second output. A run that consumed
 * degraded input cannot report itself clean, and a run that did not succeed cannot leave its output
 * standing as the current version of the dataset.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("pipeline_runs")
public class PipelineRun {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The dataset version this run produces. */
    private UUID dataProductId;
    /** Why the run happened. */
    private PipelineRunKind runKind;
    /**
     * Digest of the product version, code and configuration together; a retry with the same one
     * returns this row rather than a second output.
     */
    private String specificationDigest;
    /** Which version of the transformation code ran. */
    private String codeVersion;
    /** Digest of the deterministic parameters it ran with. */
    private String configDigest;
    /** The partition produced, absent for an unpartitioned product. */
    private @Nullable String partitionKey;
    /** Latest input time the run was allowed to read. */
    private Instant inputWatermark;
    /** Latest output time the run produced. */
    private Instant outputWatermark;
    /**
     * Deletion state pinned before reading, so a later erasure can invalidate the output rather
     * than silently changing it.
     */
    private @Nullable Instant deletionWatermark;
    /** Where the run stands. */
    private PipelineRunState state;
    /** Standing of its quality checks. */
    private DataQualityState qualityState;
    /** Whether it knowingly read an input that was not clean; such a run may not report PASS. */
    private boolean acceptedDegradedInput;
    /** Why reading the degraded input was acceptable. */
    private @Nullable String degradedInputReason;
    /** How many rows it read. */
    private @Nullable Long rowsRead;
    /** How many rows it wrote. */
    private @Nullable Long rowsWritten;
    /** How many rows it refused. */
    private @Nullable Long rowsRejected;
    /** Where the output snapshot lives. */
    private @Nullable String outputSnapshotReference;
    /** Digest of that output, so reproduction can be checked. */
    private @Nullable String outputDigest;
    /** Whether this run output is what consumers read; only a successful run may be. */
    private boolean publishedAsCurrent;
    /** How many attempts have been made. */
    private int attemptCount;
    /** Worker currently holding the run. */
    private @Nullable String leaseOwner;
    /** UTC instant that lease may be reclaimed. */
    private @Nullable Instant leaseExpiresAt;
    /** Monotonic token so a worker whose lease expired cannot still write. */
    private @Nullable Long fencingToken;
    /** What kind of failure ended the run. */
    private @Nullable String failureClass;
    /** UTC instant started. */
    private @Nullable Instant startedAt;
    /** UTC instant finished. */
    private @Nullable Instant finishedAt;
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
