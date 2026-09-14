package dev.ngb.backend.review.internal.repository.aspect;

import dev.ngb.backend.review.internal.model.aspect.AspectTaxonomyVersion;
import dev.ngb.backend.review.internal.model.aspect.AspectTaxonomyStatus;
import org.springframework.data.repository.ListCrudRepository;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.review.internal.model.aspect.AspectTaxonomyStatus;
import dev.ngb.backend.review.internal.model.aspect.AspectTaxonomyVersion;


/**
 * Reads versions of the aspect vocabulary.
 *
 * <p>Extraction resolves the active version once and stores its identifier on every run and mention,
 * because the version is what gives a stored mention its meaning.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code aspect_taxonomy_versions}.</p>
 */
public interface AspectTaxonomyVersionRepository extends ListCrudRepository<AspectTaxonomyVersion, UUID> {

    /**
     * Finds the version currently in use for a taxonomy.
     *
     * <p>Spring derives {@code WHERE taxonomy_key = ? AND status = ?}, matching
     * {@code uk_aspect_taxonomy_versions_active} when the status is {@code ACTIVE}.</p>
     *
     * @param taxonomyKey taxonomy
     * @param status status to match, normally {@code ACTIVE}
     * @return the active version, when one exists
     */
    Optional<AspectTaxonomyVersion> findByTaxonomyKeyAndStatus(String taxonomyKey,
            AspectTaxonomyStatus status);

    /**
     * Finds one numbered version of a taxonomy.
     *
     * <p>Spring derives {@code WHERE taxonomy_key = ? AND taxonomy_version = ?}, matching
     * {@code uk_aspect_taxonomy_versions_identity}.</p>
     *
     * @param taxonomyKey taxonomy
     * @param taxonomyVersion version within it
     * @return the version, when it exists
     */
    Optional<AspectTaxonomyVersion> findByTaxonomyKeyAndTaxonomyVersion(String taxonomyKey,
            int taxonomyVersion);
}
