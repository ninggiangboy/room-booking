package dev.ngb.backend.analytics.internal.repository.experiment;

import dev.ngb.backend.analytics.internal.model.experiment.ExperimentAction;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the commands issued against an experiment.
 *
 * <p>These rows record that somebody asked, not that the experiment changed. The command key makes a
 * replay a no-op, and the approver column is what a rollout cannot be recorded without.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code experiment_actions}.</p>
 */
public interface ExperimentActionRepository extends ListCrudRepository<ExperimentAction, UUID> {

    /**
     * Finds a command already recorded under one key.
     *
     * @param experimentDefinitionId the experiment
     * @param commandKey the idempotency key
     * @return the command, when it has been issued
     */
    Optional<ExperimentAction> findByExperimentDefinitionIdAndCommandKey(
            UUID experimentDefinitionId, String commandKey);

    /**
     * Lists the commands issued against one experiment, newest first.
     *
     * @param experimentDefinitionId the experiment
     * @return possibly empty list, most recent first
     */
    List<ExperimentAction> findByExperimentDefinitionIdOrderByRequestedAtDesc(
            UUID experimentDefinitionId);

    /**
     * Lists commands not yet carried out, oldest first, for the lifecycle worker.
     *
     * @param applied normally {@code false}
     * @return possibly empty list, oldest request first
     */
    List<ExperimentAction> findByAppliedOrderByRequestedAtAsc(boolean applied);

    /**
     * Lists the commands guardrail automation issued, for the review it is owed.
     *
     * <pre>{@code
     * SELECT * FROM experiment_actions
     * WHERE actor_kind = 'GUARDRAIL_AUTOMATION' AND requested_at >= :since
     * ORDER BY requested_at DESC
     * }</pre>
     *
     * <p>Automation may pause or stop a treatment without waiting for a person, so every one of these
     * is a decision a person still has to look at afterwards.</p>
     *
     * @param since how far back to look
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM experiment_actions
            WHERE actor_kind = 'GUARDRAIL_AUTOMATION' AND requested_at >= :since
            ORDER BY requested_at DESC
            """)
    List<ExperimentAction> findAutomated(@Param("since") Instant since);
}
