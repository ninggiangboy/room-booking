package dev.ngb.backend.repository;

import dev.ngb.backend.model.AmenityTranslation;
import dev.ngb.backend.model.AmenityTranslationId;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads what amenity terms are called in each language.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} operate on the composite
 * {@link AmenityTranslationId} key and the {@code amenity_translations} table.</p>
 */
public interface AmenityTranslationRepository
        extends ListCrudRepository<AmenityTranslation, AmenityTranslationId> {

    /**
     * Returns every language version of one amenity term.
     *
     * <p>Spring derives {@code WHERE amenity_definition_id = ?} from the composite-key property
     * path. An absent locale means the term has not been translated yet, which callers handle by
     * falling back to a default language rather than showing a raw key.</p>
     *
     * @param amenityDefinitionId term whose labels are wanted
     * @return possibly empty list of translations
     */
    List<AmenityTranslation> findAllByIdAmenityDefinitionId(UUID amenityDefinitionId);
}
