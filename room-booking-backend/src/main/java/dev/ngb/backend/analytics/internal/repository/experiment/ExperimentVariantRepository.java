package dev.ngb.backend.analytics.internal.repository.experiment;

import dev.ngb.backend.analytics.internal.model.experiment.ExperimentVariant;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the arms of an epoch and the buckets each holds.
 *
 * <p>The bucket lookup is the assignment path: the allocator hashes the unit, and this is what turns
 * that number into an arm. Once the epoch is sealed these rows cannot change at all.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code experiment_variants}.</p>
 */
public interface ExperimentVariantRepository extends ListCrudRepository<ExperimentVariant, UUID> {

    /**
     * Lists the arms of one epoch.
     *
     * @param experimentEpochId the epoch
     * @return possibly empty list of arms
     */
    List<ExperimentVariant> findByExperimentEpochId(UUID experimentEpochId);

    /**
     * Finds one arm by key.
     *
     * @param experimentEpochId the epoch
     * @param variantKey the arm
     * @return the arm, when the epoch declares it
     */
    Optional<ExperimentVariant> findByExperimentEpochIdAndVariantKey(UUID experimentEpochId,
            String variantKey);

    /**
     * Finds the baseline arm of one epoch.
     *
     * @param experimentEpochId the epoch
     * @param isControl normally {@code true}
     * @return the control arm, of which an approved epoch has exactly one
     */
    Optional<ExperimentVariant> findByExperimentEpochIdAndIsControl(UUID experimentEpochId,
            boolean isControl);

    /**
     * Finds the arm a bucket falls into.
     *
     * <pre>{@code
     * SELECT * FROM experiment_variants
     * WHERE experiment_epoch_id = :experimentEpochId AND bucket_range @> :bucket
     * }</pre>
     *
     * <p>An epoch need not allocate every bucket. A unit whose bucket falls in no arm is simply not in
     * the experiment, which is how a holdback is expressed.</p>
     *
     * @param experimentEpochId the epoch
     * @param bucket the hash output
     * @return the arm holding that bucket, when one does
     */
    @Query("""
            SELECT * FROM experiment_variants
            WHERE experiment_epoch_id = :experimentEpochId AND bucket_range @> :bucket
            """)
    Optional<ExperimentVariant> findByBucket(@Param("experimentEpochId") UUID experimentEpochId,
            @Param("bucket") int bucket);
}
