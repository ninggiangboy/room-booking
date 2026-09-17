package dev.ngb.backend.analytics.internal.repository.contract;

import dev.ngb.backend.analytics.internal.model.contract.DataProductDependency;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.UUID;

/**
 * Reads the declared lineage edges between dataset versions.
 *
 * <p>Both directions matter. A run resolves what it must read; a privacy review resolves who reads
 * a dataset before its classification is loosened.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code data_product_dependencies}.</p>
 */
public interface DataProductDependencyRepository extends ListCrudRepository<DataProductDependency, UUID> {

    /**
     * Lists what one dataset version declares that it reads.
     *
     * @param dataProductId the downstream dataset version
     * @return possibly empty list of edges
     */
    List<DataProductDependency> findByDataProductId(UUID dataProductId);

    /**
     * Lists the dataset versions that read a given one.
     *
     * @param upstreamDataProductId the upstream dataset version
     * @return possibly empty list of edges
     */
    List<DataProductDependency> findByUpstreamDataProductId(UUID upstreamDataProductId);

    /**
     * Lists the edges that carry an approved declassification, for privacy review.
     *
     * <pre>{@code
     * SELECT * FROM data_product_dependencies
     * WHERE declassification_approval IS NOT NULL
     * ORDER BY created_at DESC
     * }</pre>
     *
     * @return possibly empty list, most recently approved first
     */
    @Query("""
            SELECT * FROM data_product_dependencies
            WHERE declassification_approval IS NOT NULL
            ORDER BY created_at DESC
            """)
    List<DataProductDependency> findDeclassified();
}
