package dev.ngb.backend.repository;

import dev.ngb.backend.model.ExperimentDefinition;
import dev.ngb.backend.model.ExperimentStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the registered experiments and the namespaces they compete for.
 *
 * <p>The namespace query is what collision detection is built on: before an epoch is activated,
 * somebody has to be able to see what else is already treating the same surface.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code experiment_definitions}.</p>
 */
public interface ExperimentDefinitionRepository extends ListCrudRepository<ExperimentDefinition, UUID> {

    /**
     * Finds one experiment by its stable key.
     *
     * @param experimentKey the experiment
     * @return the experiment, when it is registered
     */
    Optional<ExperimentDefinition> findByExperimentKey(String experimentKey);

    /**
     * Lists the experiments currently delivering treatment in one namespace.
     *
     * <pre>{@code
     * SELECT * FROM experiment_definitions
     * WHERE namespace = :namespace AND status IN ('RUNNING', 'PAUSED')
     * ORDER BY experiment_key
     * }</pre>
     *
     * @param namespace the surface being contended for
     * @return possibly empty list, by experiment key
     */
    @Query("""
            SELECT * FROM experiment_definitions
            WHERE namespace = :namespace AND status IN ('RUNNING', 'PAUSED')
            ORDER BY experiment_key
            """)
    List<ExperimentDefinition> findLiveInNamespace(@Param("namespace") String namespace);

    /**
     * Lists the experiments in one state, for the console.
     *
     * @param status the state to list
     * @return possibly empty list, most recently changed first
     */
    List<ExperimentDefinition> findByStatusOrderByUpdatedAtDesc(ExperimentStatus status);

    /**
     * Lists the experiments holding a long-term holdout.
     *
     * @param longTermHoldout normally {@code true}
     * @return possibly empty list of experiments
     */
    List<ExperimentDefinition> findByLongTermHoldout(boolean longTermHoldout);
}
