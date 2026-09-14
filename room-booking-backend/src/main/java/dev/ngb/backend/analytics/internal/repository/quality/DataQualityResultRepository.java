package dev.ngb.backend.analytics.internal.repository.quality;

import dev.ngb.backend.analytics.internal.model.quality.DataQualityResult;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.analytics.internal.model.quality.DataQualityResult;


/**
 * Reads what the checks found about a run.
 *
 * <p>A standing failure keeps its run from reporting itself clean, so the unwaived-failure query is
 * what an operator reaches for before asking why a dataset did not publish.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code data_quality_results}.</p>
 */
public interface DataQualityResultRepository extends ListCrudRepository<DataQualityResult, UUID> {

    /**
     * Lists the results of one run.
     *
     * @param pipelineRunId the run
     * @return possibly empty list of results
     */
    List<DataQualityResult> findByPipelineRunId(UUID pipelineRunId);

    /**
     * Lists the failures standing against one run that nobody has waived.
     *
     * <pre>{@code
     * SELECT * FROM data_quality_results
     * WHERE pipeline_run_id = :pipelineRunId AND status = 'FAIL' AND owner_action <> 'WAIVED'
     * }</pre>
     *
     * @param pipelineRunId the run
     * @return possibly empty list of standing failures
     */
    @Query("""
            SELECT * FROM data_quality_results
            WHERE pipeline_run_id = :pipelineRunId AND status = 'FAIL' AND owner_action <> 'WAIVED'
            """)
    List<DataQualityResult> findStandingFailures(@Param("pipelineRunId") UUID pipelineRunId);

    /**
     * Lists recent results for one check, for the trend an owner watches.
     *
     * <pre>{@code
     * SELECT * FROM data_quality_results
     * WHERE data_quality_check_id = :checkId AND evaluated_at >= :since
     * ORDER BY evaluated_at DESC
     * }</pre>
     *
     * @param checkId the check
     * @param since how far back to look
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM data_quality_results
            WHERE data_quality_check_id = :checkId AND evaluated_at >= :since
            ORDER BY evaluated_at DESC
            """)
    List<DataQualityResult> findRecent(@Param("checkId") UUID checkId,
            @Param("since") Instant since);
}
