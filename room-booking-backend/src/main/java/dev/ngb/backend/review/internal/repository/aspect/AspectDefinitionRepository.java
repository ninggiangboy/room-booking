package dev.ngb.backend.review.internal.repository.aspect;

import dev.ngb.backend.review.internal.model.aspect.AspectDefinition;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the aspects within a taxonomy version.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code aspect_definitions}.</p>
 */
public interface AspectDefinitionRepository extends ListCrudRepository<AspectDefinition, UUID> {

    /**
     * Lists the aspects of one taxonomy version.
     *
     * <p>Spring derives {@code WHERE aspect_taxonomy_version_id = ?}. The set is fixed once the version
     * is active, so this list is stable for as long as anything points at that version.</p>
     *
     * @param aspectTaxonomyVersionId taxonomy version
     * @return possibly empty list
     */
    List<AspectDefinition> findByAspectTaxonomyVersionId(UUID aspectTaxonomyVersionId);

    /**
     * Finds one aspect within a taxonomy version.
     *
     * <p>Spring derives {@code WHERE aspect_taxonomy_version_id = ? AND aspect_code = ?}, matching
     * {@code uk_aspect_definitions_code}.</p>
     *
     * @param aspectTaxonomyVersionId taxonomy version
     * @param aspectCode aspect
     * @return the definition, when it exists in that version
     */
    Optional<AspectDefinition> findByAspectTaxonomyVersionIdAndAspectCode(UUID aspectTaxonomyVersionId,
            String aspectCode);
}
