package dev.ngb.backend.ml.internal.repository.model;

import dev.ngb.backend.ml.internal.model.model.TrainingDatasetManifest;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.ml.internal.model.model.TrainingDatasetManifest;


/**
 * Reads the immutable descriptions of dataset builds.
 *
 * <p>A build is resolved by its specification digest so a retry returns the manifest that already
 * exists, and by its reuse state so a release cannot be cut from a dataset an erasure invalidated.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code training_dataset_manifests}.</p>
 */
public interface TrainingDatasetManifestRepository extends ListCrudRepository<TrainingDatasetManifest, UUID> {

    /**
     * Finds one exact version of a dataset.
     *
     * @param datasetKey the dataset
     * @param semanticVersion the version
     * @return the manifest, when it exists
     */
    Optional<TrainingDatasetManifest> findByDatasetKeyAndSemanticVersion(String datasetKey,
            short semanticVersion);

    /**
     * Finds the manifest for a specification, so a restarted build does not create a second.
     *
     * @param specificationDigest digest over the build specification and its snapshots
     * @return the existing manifest, when the same specification has been built
     */
    Optional<TrainingDatasetManifest> findBySpecificationDigest(String specificationDigest);

    /**
     * Lists the builds of one dataset, newest first.
     *
     * @param datasetKey the dataset
     * @return possibly empty list, most recently built first
     */
    List<TrainingDatasetManifest> findByDatasetKeyOrderByBuiltAtDesc(String datasetKey);

    /**
     * Lists the builds a later erasure invalidated, so the models released against them can be
     * assessed rather than left standing unexamined.
     *
     * <pre>{@code
     * SELECT * FROM training_dataset_manifests
     * WHERE reuse_state <> 'REUSABLE'
     * ORDER BY invalidated_at DESC
     * }</pre>
     *
     * @return possibly empty list, most recently invalidated first
     */
    @Query("""
            SELECT * FROM training_dataset_manifests
            WHERE reuse_state <> 'REUSABLE'
            ORDER BY invalidated_at DESC
            """)
    List<TrainingDatasetManifest> findInvalidated();

    /**
     * Lists the builds whose deletion watermark predates a given instant, which is how a new
     * erasure finds the datasets it has to invalidate.
     *
     * <pre>{@code
     * SELECT * FROM training_dataset_manifests
     * WHERE reuse_state = 'REUSABLE' AND deletion_watermark_at < :erasureAt
     * ORDER BY built_at
     * }</pre>
     *
     * @param erasureAt the instant the erasure was requested
     * @return possibly empty list, oldest build first
     */
    @Query("""
            SELECT * FROM training_dataset_manifests
            WHERE reuse_state = 'REUSABLE' AND deletion_watermark_at < :erasureAt
            ORDER BY built_at
            """)
    List<TrainingDatasetManifest> findReusableBefore(@Param("erasureAt") Instant erasureAt);
}
