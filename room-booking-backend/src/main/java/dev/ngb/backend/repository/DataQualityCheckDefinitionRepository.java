package dev.ngb.backend.repository;

import dev.ngb.backend.model.DataQualityCheckDefinition;
import dev.ngb.backend.model.QualityCheckStatus;
import dev.ngb.backend.model.QualityDimension;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the versioned quality checks defined against a dataset.
 *
 * <p>A run evaluates the active checks for its dataset version. A retired check is kept because the
 * results it produced still name it.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code data_quality_check_definitions}.</p>
 */
public interface DataQualityCheckDefinitionRepository extends ListCrudRepository<DataQualityCheckDefinition, UUID> {

    /**
     * Lists the checks a run should evaluate.
     *
     * @param dataProductId the dataset version
     * @param status normally {@code ACTIVE}
     * @return possibly empty list of checks
     */
    List<DataQualityCheckDefinition> findByDataProductIdAndStatus(UUID dataProductId,
            QualityCheckStatus status);

    /**
     * Finds one exact version of a check.
     *
     * @param checkKey the check
     * @param checkVersion the version
     * @return the check, when it is registered
     */
    Optional<DataQualityCheckDefinition> findByCheckKeyAndCheckVersion(String checkKey,
            short checkVersion);

    /**
     * Lists the checks of one dimension against a dataset, such as every privacy check.
     *
     * @param dataProductId the dataset version
     * @param qualityDimension the dimension
     * @return possibly empty list of checks
     */
    List<DataQualityCheckDefinition> findByDataProductIdAndQualityDimension(UUID dataProductId,
            QualityDimension qualityDimension);
}
