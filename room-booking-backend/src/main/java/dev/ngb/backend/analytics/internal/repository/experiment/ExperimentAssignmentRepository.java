package dev.ngb.backend.analytics.internal.repository.experiment;

import dev.ngb.backend.analytics.internal.model.experiment.ExperimentAssignment;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.analytics.internal.model.experiment.ExperimentAssignment;


/**
 * Reads and writes the record that a unit was bucketed into an arm.
 *
 * <p>Immutable and unique per epoch and unit. Two concurrent requests race on that constraint: the
 * winner writes, and the loser reads what the winner wrote rather than bucketing the unit twice.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code experiment_assignments}.</p>
 */
public interface ExperimentAssignmentRepository extends ListCrudRepository<ExperimentAssignment, UUID> {

    /**
     * Finds the assignment a unit already has in one epoch.
     *
     * @param experimentEpochId the epoch
     * @param unitPseudonym the pseudonymous unit
     * @return the assignment, when the unit has been bucketed
     */
    Optional<ExperimentAssignment> findByExperimentEpochIdAndUnitPseudonym(UUID experimentEpochId,
            String unitPseudonym);

    /**
     * Lists every assignment one unit carries, newest first.
     *
     * @param unitPseudonym the pseudonymous unit
     * @return possibly empty list, most recent first
     */
    List<ExperimentAssignment> findByUnitPseudonymOrderByAssignedAtDesc(String unitPseudonym);

    /**
     * Counts the units in each arm of one epoch, for the sample-ratio check.
     *
     * <pre>{@code
     * SELECT experiment_variant_id AS variantId, count(*) AS unitCount
     * FROM experiment_assignments
     * WHERE experiment_epoch_id = :experimentEpochId
     * GROUP BY experiment_variant_id
     * }</pre>
     *
     * <p>A ratio that diverges from the declared allocation means the randomisation did not do what it
     * claimed, which makes every comparison downstream of it suspect.</p>
     *
     * @param experimentEpochId the epoch
     * @return one row per arm that has any units
     */
    @Query("""
            SELECT experiment_variant_id AS variantId, count(*) AS unitCount
            FROM experiment_assignments
            WHERE experiment_epoch_id = :experimentEpochId
            GROUP BY experiment_variant_id
            """)
    List<VariantCount> countByVariant(@Param("experimentEpochId") UUID experimentEpochId);

    /**
     * How many units one arm of an epoch holds.
     *
     * @param variantId the arm
     * @param unitCount how many units were assigned to it
     */
    record VariantCount(UUID variantId, long unitCount) {
    }
}
