package dev.ngb.backend.repository;

import dev.ngb.backend.model.DataProductVersion;
import dev.ngb.backend.model.DataContractStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the registered versions of an analytical dataset.
 *
 * <p>Every pipeline, metric and quality check resolves its dataset through this table, so a reader
 * that does not find a current version has to fail rather than guess which one was meant.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code data_product_registry}.</p>
 */
public interface DataProductVersionRepository extends ListCrudRepository<DataProductVersion, UUID> {

    /**
     * Finds the version consumers read by default.
     *
     * @param datasetKey the dataset
     * @param status normally {@code CURRENT}
     * @return the current version, when one is published
     */
    Optional<DataProductVersion> findByDatasetKeyAndStatus(String datasetKey,
            DataContractStatus status);

    /**
     * Finds one exact version of a dataset.
     *
     * @param datasetKey the dataset
     * @param semanticVersion the version, as major.minor.patch
     * @return the version, when it is registered
     */
    Optional<DataProductVersion> findByDatasetKeyAndSemanticVersion(String datasetKey,
            String semanticVersion);

    /**
     * Lists every version of one dataset, newest registration first.
     *
     * @param datasetKey the dataset
     * @return possibly empty list, most recently created first
     */
    List<DataProductVersion> findByDatasetKeyOrderByCreatedAtDesc(String datasetKey);

    /**
     * Lists the datasets one owner is accountable for.
     *
     * <pre>{@code
     * SELECT * FROM data_product_registry
     * WHERE business_owner = :owner AND status IN ('CURRENT', 'DEPRECATED')
     * ORDER BY dataset_key
     * }</pre>
     *
     * @param owner the accountable business owner
     * @return possibly empty list, by dataset key
     */
    @Query("""
            SELECT * FROM data_product_registry
            WHERE business_owner = :owner AND status IN ('CURRENT', 'DEPRECATED')
            ORDER BY dataset_key
            """)
    List<DataProductVersion> findOwned(@Param("owner") String owner);
}
