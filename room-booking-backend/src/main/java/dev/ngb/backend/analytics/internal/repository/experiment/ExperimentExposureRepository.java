package dev.ngb.backend.analytics.internal.repository.experiment;

import dev.ngb.backend.analytics.internal.model.experiment.ExperimentExposure;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes the record that a treatment could have reached a unit.
 *
 * <p>Exposure is the denominator of everything measured on the treated, so it is written by the
 * component that knows delivery happened, deduplicated by the key the epoch declared, and never
 * inferred from the assignment alone.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code experiment_exposures}.</p>
 */
public interface ExperimentExposureRepository extends ListCrudRepository<ExperimentExposure, UUID> {

    /**
     * Finds the exposure already recorded under one dedupe key.
     *
     * @param experimentAssignmentId the assignment
     * @param exposureDedupeKey the declared dedupe key
     * @return the exposure, when it has been recorded
     */
    Optional<ExperimentExposure> findByExperimentAssignmentIdAndExposureDedupeKey(
            UUID experimentAssignmentId, String exposureDedupeKey);

    /**
     * Lists the exposures of one assignment, oldest first.
     *
     * @param experimentAssignmentId the assignment
     * @return possibly empty list, oldest first
     */
    List<ExperimentExposure> findByExperimentAssignmentIdOrderByOccurredAtAsc(
            UUID experimentAssignmentId);

    /**
     * Counts the distinct units exposed in one epoch up to a cutoff.
     *
     * <pre>{@code
     * SELECT count(DISTINCT e.experiment_assignment_id)
     * FROM experiment_exposures e
     * JOIN experiment_assignments a ON a.id = e.experiment_assignment_id
     * WHERE a.experiment_epoch_id = :experimentEpochId AND e.occurred_at <= :cutoff
     * }</pre>
     *
     * @param experimentEpochId the epoch
     * @param cutoff the analysis data cutoff
     * @return how many assigned units were actually reached
     */
    @Query("""
            SELECT count(DISTINCT e.experiment_assignment_id)
            FROM experiment_exposures e
            JOIN experiment_assignments a ON a.id = e.experiment_assignment_id
            WHERE a.experiment_epoch_id = :experimentEpochId AND e.occurred_at <= :cutoff
            """)
    long countExposedUnits(@Param("experimentEpochId") UUID experimentEpochId,
            @Param("cutoff") Instant cutoff);

    /**
     * Counts the exposures in one epoch that fell back to something other than the assigned arm.
     *
     * <pre>{@code
     * SELECT count(*)
     * FROM experiment_exposures e
     * JOIN experiment_assignments a ON a.id = e.experiment_assignment_id
     * WHERE a.experiment_epoch_id = :experimentEpochId AND e.fallback_applied = true
     * }</pre>
     *
     * <p>A high count means the treated group is diluted with untreated units, which biases every
     * effect toward zero while the delivery dashboard still reads as healthy.</p>
     *
     * @param experimentEpochId the epoch
     * @return how many deliveries did not carry the assigned treatment
     */
    @Query("""
            SELECT count(*)
            FROM experiment_exposures e
            JOIN experiment_assignments a ON a.id = e.experiment_assignment_id
            WHERE a.experiment_epoch_id = :experimentEpochId AND e.fallback_applied = true
            """)
    long countFallbacks(@Param("experimentEpochId") UUID experimentEpochId);

    /**
     * Lists exposures whose retention horizon has passed, for the retention worker.
     *
     * <pre>{@code
     * SELECT * FROM experiment_exposures
     * WHERE expires_at <= :at AND retention_class <> 'LEGAL_HOLD'
     * ORDER BY expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * @param at instant to compare against
     * @param batchSize how many to claim at once
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT * FROM experiment_exposures
            WHERE expires_at <= :at AND retention_class <> 'LEGAL_HOLD'
            ORDER BY expires_at
            LIMIT :batchSize
            """)
    List<ExperimentExposure> findExpired(@Param("at") Instant at,
            @Param("batchSize") int batchSize);
}
