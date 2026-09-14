package dev.ngb.backend.analytics.internal.repository.pipeline;

import dev.ngb.backend.analytics.internal.model.pipeline.PipelineRunInput;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.UUID;

/**
 * Reads the upstream snapshots a run actually read.
 *
 * <p>This is the lineage hop that makes a number reproducible: the snapshot and watermark are what
 * was read, not what today would be read.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code pipeline_run_inputs}.</p>
 */
public interface PipelineRunInputRepository extends ListCrudRepository<PipelineRunInput, UUID> {

    /**
     * Lists the inputs of one run.
     *
     * @param pipelineRunId the run
     * @return possibly empty list of inputs
     */
    List<PipelineRunInput> findByPipelineRunId(UUID pipelineRunId);

    /**
     * Lists the runs that consumed one upstream run output.
     *
     * @param upstreamPipelineRunId the upstream run
     * @return possibly empty list of inputs
     */
    List<PipelineRunInput> findByUpstreamPipelineRunId(UUID upstreamPipelineRunId);

    /**
     * Lists the inputs of one run that were known not to be clean.
     *
     * @param pipelineRunId the run
     * @param degraded normally {@code true}
     * @return possibly empty list of degraded inputs
     */
    List<PipelineRunInput> findByPipelineRunIdAndDegraded(UUID pipelineRunId, boolean degraded);
}
