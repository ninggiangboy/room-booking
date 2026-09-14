package dev.ngb.backend.analytics.internal.repository.experiment;

import dev.ngb.backend.analytics.internal.model.experiment.ExperimentAnalysisRun;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.analytics.internal.model.experiment.ExperimentAnalysisRun;


/**
 * Reads the analyses of an epoch.
 *
 * <p>Each row is one look at one cutoff under one plan version. The look number and the sequential
 * method are stored together because the second look is only legitimate if something paid for it.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code experiment_analysis_runs}.</p>
 */
public interface ExperimentAnalysisRunRepository extends ListCrudRepository<ExperimentAnalysisRun, UUID> {

    /**
     * Lists the analyses of one epoch, newest first.
     *
     * @param experimentEpochId the epoch
     * @return possibly empty list, most recent first
     */
    List<ExperimentAnalysisRun> findByExperimentEpochIdOrderByRunAtDesc(UUID experimentEpochId);

    /**
     * Finds one look under one plan version.
     *
     * @param experimentEpochId the epoch
     * @param analysisPlanVersion the plan version
     * @param lookNumber which look
     * @return the analysis, when it has been run
     */
    Optional<ExperimentAnalysisRun> findByExperimentEpochIdAndAnalysisPlanVersionAndLookNumber(
            UUID experimentEpochId, short analysisPlanVersion, int lookNumber);

    /**
     * Finds the latest analysis of one epoch that reached a conclusion.
     *
     * <pre>{@code
     * SELECT * FROM experiment_analysis_runs
     * WHERE experiment_epoch_id = :experimentEpochId AND conclusion IS NOT NULL
     * ORDER BY run_at DESC
     * LIMIT 1
     * }</pre>
     *
     * @param experimentEpochId the epoch
     * @return the latest concluded analysis, when there is one
     */
    @Query("""
            SELECT * FROM experiment_analysis_runs
            WHERE experiment_epoch_id = :experimentEpochId AND conclusion IS NOT NULL
            ORDER BY run_at DESC
            LIMIT 1
            """)
    Optional<ExperimentAnalysisRun> findLatestConcluded(
            @Param("experimentEpochId") UUID experimentEpochId);

    /**
     * Lists analyses whose integrity checks did not hold, for the experimentation console.
     *
     * <pre>{@code
     * SELECT * FROM experiment_analysis_runs
     * WHERE srm_status = 'FAIL' OR integrity_status = 'FAIL'
     * ORDER BY run_at DESC
     * }</pre>
     *
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM experiment_analysis_runs
            WHERE srm_status = 'FAIL' OR integrity_status = 'FAIL'
            ORDER BY run_at DESC
            """)
    List<ExperimentAnalysisRun> findCompromised();
}
