package dev.ngb.backend.repository;

import dev.ngb.backend.model.ExperimentExclusion;
import dev.ngb.backend.model.ExperimentRelationKind;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads the declared relationships between one epoch and other experiments.
 *
 * <p>Read before activation, so a collision is found while it is still a scheduling problem rather
 * than after two experiments have been reporting each other effects for a fortnight.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code experiment_exclusions}.</p>
 */
public interface ExperimentExclusionRepository extends ListCrudRepository<ExperimentExclusion, UUID> {

    /**
     * Lists what one epoch declares about other experiments.
     *
     * @param experimentEpochId the epoch
     * @return possibly empty list of declarations
     */
    List<ExperimentExclusion> findByExperimentEpochId(UUID experimentEpochId);

    /**
     * Lists the epochs that name one experiment.
     *
     * @param relatedExperimentDefinitionId the experiment named
     * @return possibly empty list of declarations
     */
    List<ExperimentExclusion> findByRelatedExperimentDefinitionId(
            UUID relatedExperimentDefinitionId);

    /**
     * Lists the experiments one epoch declares itself incompatible with.
     *
     * @param experimentEpochId the epoch
     * @param relationKind normally {@code MUTUALLY_EXCLUSIVE}
     * @return possibly empty list of declarations
     */
    List<ExperimentExclusion> findByExperimentEpochIdAndRelationKind(UUID experimentEpochId,
            ExperimentRelationKind relationKind);
}
