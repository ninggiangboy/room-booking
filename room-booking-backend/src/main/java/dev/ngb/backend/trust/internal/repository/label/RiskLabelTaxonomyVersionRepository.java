package dev.ngb.backend.trust.internal.repository.label;

import dev.ngb.backend.trust.internal.model.label.RiskLabelTaxonomyVersion;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads label vocabularies.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_label_taxonomy_versions}.</p>
 */
public interface RiskLabelTaxonomyVersionRepository extends ListCrudRepository<RiskLabelTaxonomyVersion, UUID> {

    /**
     * Finds one vocabulary version.
     *
     * <pre>{@code
     * SELECT * FROM risk_label_taxonomy_versions
     * WHERE taxonomy_key = :taxonomyKey AND taxonomy_version = :taxonomyVersion
     * }</pre>
     *
     * @param taxonomyKey vocabulary key
     * @param taxonomyVersion version
     * @return the version, when it exists
     */
    Optional<RiskLabelTaxonomyVersion> findByTaxonomyKeyAndTaxonomyVersion(String taxonomyKey,
                                                                          int taxonomyVersion);

    /**
     * Finds the vocabulary version currently in use.
     *
     * <pre>{@code
     * SELECT * FROM risk_label_taxonomy_versions
     * WHERE taxonomy_key = :taxonomyKey AND status = 'ACTIVE'
     * ORDER BY taxonomy_version DESC
     * LIMIT 1
     * }</pre>
     *
     * @param taxonomyKey vocabulary key
     * @return the active version, when there is one
     */
    @Query("""
            SELECT *
            FROM risk_label_taxonomy_versions
            WHERE taxonomy_key = :taxonomyKey AND status = 'ACTIVE'
            ORDER BY taxonomy_version DESC
            LIMIT 1
            """)
    Optional<RiskLabelTaxonomyVersion> findActive(@Param("taxonomyKey") String taxonomyKey);
}
