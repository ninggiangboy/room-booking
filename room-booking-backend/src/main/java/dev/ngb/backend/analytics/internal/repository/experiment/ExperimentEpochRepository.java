package dev.ngb.backend.analytics.internal.repository.experiment;

import dev.ngb.backend.analytics.internal.model.experiment.ExperimentEpoch;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the immutable design a unit is randomised under.
 *
 * <p>Assignment resolves the running epoch here, and everything it needs to reproduce a bucket --
 * the salt version, the allocator version and the bucket count -- comes from this row rather than
 * from configuration that could have changed since.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code experiment_epochs}.</p>
 */
public interface ExperimentEpochRepository extends ListCrudRepository<ExperimentEpoch, UUID> {

    /**
     * Finds the epoch currently assigning units for one experiment.
     *
     * <pre>{@code
     * SELECT * FROM experiment_epochs
     * WHERE experiment_definition_id = :experimentDefinitionId AND state = 'RUNNING'
     * }</pre>
     *
     * @param experimentDefinitionId the experiment
     * @return the running epoch, when one is
     */
    @Query("""
            SELECT * FROM experiment_epochs
            WHERE experiment_definition_id = :experimentDefinitionId AND state = 'RUNNING'
            """)
    Optional<ExperimentEpoch> findRunning(
            @Param("experimentDefinitionId") UUID experimentDefinitionId);

    /**
     * Finds one epoch of an experiment by number.
     *
     * @param experimentDefinitionId the experiment
     * @param epochNumber which epoch, starting at one
     * @return the epoch, when it exists
     */
    Optional<ExperimentEpoch> findByExperimentDefinitionIdAndEpochNumber(
            UUID experimentDefinitionId, int epochNumber);

    /**
     * Lists every epoch of one experiment, newest first.
     *
     * @param experimentDefinitionId the experiment
     * @return possibly empty list, newest epoch first
     */
    List<ExperimentEpoch> findByExperimentDefinitionIdOrderByEpochNumberDesc(
            UUID experimentDefinitionId);

    /**
     * Lists running epochs past their scheduled stop, for the lifecycle worker.
     *
     * <pre>{@code
     * SELECT * FROM experiment_epochs
     * WHERE state = 'RUNNING' AND scheduled_stop_at <= :at
     * ORDER BY scheduled_stop_at
     * }</pre>
     *
     * @param at instant to compare against
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT * FROM experiment_epochs
            WHERE state = 'RUNNING' AND scheduled_stop_at <= :at
            ORDER BY scheduled_stop_at
            """)
    List<ExperimentEpoch> findDueToStop(@Param("at") Instant at);

    /**
     * Lists running epochs that have not yet met their minimum runtime.
     *
     * <pre>{@code
     * SELECT * FROM experiment_epochs
     * WHERE state = 'RUNNING' AND started_at + make_interval(days => minimum_runtime_days) > :at
     * ORDER BY started_at
     * }</pre>
     *
     * <p>Reading before the minimum runtime is not forbidden, but a decision taken there is a decision
     * taken on a design that has not yet collected what it said it needed.</p>
     *
     * @param at instant to compare against
     * @return possibly empty list, oldest start first
     */
    @Query("""
            SELECT * FROM experiment_epochs
            WHERE state = 'RUNNING' AND started_at + make_interval(days => minimum_runtime_days) > :at
            ORDER BY started_at
            """)
    List<ExperimentEpoch> findBeforeMinimumRuntime(@Param("at") Instant at);
}
