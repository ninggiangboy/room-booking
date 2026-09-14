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
import org.springframework.data.relational.core.mapping.Table;


/**
 * One upstream snapshot a run actually read.
 *
 * <p>Append-only. Reproducing a number means reading the same snapshots at the same watermarks,
 * which is only possible if the run recorded them rather than recomputing them from today.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("pipeline_run_inputs")
public class PipelineRunInput {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The run that read this input. */
    private UUID pipelineRunId;
    /** The dataset version that was read. */
    private UUID upstreamDataProductId;
    /** The run that produced it, where the input was itself computed. */
    private @Nullable UUID upstreamPipelineRunId;
    /** The exact snapshot read, which is what makes reproduction possible. */
    private String snapshotReference;
    /** Watermark of that snapshot. */
    private Instant watermark;
    /** Digest of the input, so an altered snapshot is detectable. */
    private @Nullable String inputDigest;
    /** How many rows were read from it. */
    private @Nullable Long rowCount;
    /** Whether this input was known not to be clean when it was read. */
    private boolean degraded;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
