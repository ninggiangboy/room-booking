package dev.ngb.backend.ml.internal.repository.model;

import dev.ngb.backend.ml.internal.model.model.ModelVersion;
import dev.ngb.backend.ml.internal.model.model.ModelVersionStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.ml.internal.model.model.ModelVersion;
import dev.ngb.backend.ml.internal.model.model.ModelVersionStatus;


/**
 * Reads the registry of model versions.
 *
 * <p>Every prediction, evaluation, approval and route names a version from here. The status is
 * what decides whether a version may be routed traffic at all, so it is read rather than
 * assumed.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code model_versions}.</p>
 */
public interface ModelVersionRepository extends ListCrudRepository<ModelVersion, UUID> {

    /**
     * Finds one exact version of a model.
     *
     * @param modelKey the model
     * @param modelVersion the version
     * @return the registered version, when it exists
     */
    Optional<ModelVersion> findByModelKeyAndModelVersion(String modelKey, String modelVersion);

    /**
     * Finds a version by its artifact, so re-registering the same bytes returns the same row.
     *
     * @param modelKey the model
     * @param artifactChecksum checksum of the artifact
     * @return the existing version, when the same artifact is registered
     */
    Optional<ModelVersion> findByModelKeyAndArtifactChecksum(String modelKey,
            String artifactChecksum);

    /**
     * Lists every version of one model in a given state.
     *
     * @param modelKey the model
     * @param status the lifecycle state
     * @return possibly empty list
     */
    List<ModelVersion> findByModelKeyAndStatus(String modelKey, ModelVersionStatus status);

    /**
     * Lists every version of one model, newest registration first.
     *
     * @param modelKey the model
     * @return possibly empty list, most recently created first
     */
    List<ModelVersion> findByModelKeyOrderByCreatedAtDesc(String modelKey);

    /**
     * Lists the versions that can currently produce predictions.
     *
     * <pre>{@code
     * SELECT * FROM model_versions
     * WHERE status IN ('APPROVED', 'SHADOW', 'CANARY', 'ACTIVE')
     * ORDER BY model_key, model_version
     * }</pre>
     *
     * @return possibly empty list, by model and version
     */
    @Query("""
            SELECT * FROM model_versions
            WHERE status IN ('APPROVED', 'SHADOW', 'CANARY', 'ACTIVE')
            ORDER BY model_key, model_version
            """)
    List<ModelVersion> findServable();

    /**
     * Lists the versions trained on one dataset, which is how an invalidated manifest finds the
     * releases that have to be reassessed.
     *
     * <pre>{@code
     * SELECT * FROM model_versions
     * WHERE training_dataset_manifest_id = :manifestId
     * ORDER BY created_at DESC
     * }</pre>
     *
     * @param manifestId the dataset build
     * @return possibly empty list, most recently created first
     */
    @Query("""
            SELECT * FROM model_versions
            WHERE training_dataset_manifest_id = :manifestId
            ORDER BY created_at DESC
            """)
    List<ModelVersion> findTrainedOn(@Param("manifestId") UUID manifestId);

    /**
     * Lists the consequential versions in service, which are the ones whose approvals, fairness
     * results and provider terms a governance review has to re-examine.
     *
     * <pre>{@code
     * SELECT * FROM model_versions
     * WHERE impact_class <> 'ADVISORY' AND status IN ('CANARY', 'ACTIVE')
     * ORDER BY impact_class, model_key
     * }</pre>
     *
     * @return possibly empty list, by impact class then model
     */
    @Query("""
            SELECT * FROM model_versions
            WHERE impact_class <> 'ADVISORY' AND status IN ('CANARY', 'ACTIVE')
            ORDER BY impact_class, model_key
            """)
    List<ModelVersion> findConsequentialInService();
}
