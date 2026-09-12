package dev.ngb.backend.repository;

import dev.ngb.backend.model.AmenityDefinition;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the amenity terms available in a vocabulary version.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select and insert SQL
 * for {@code amenity_definitions}.</p>
 */
public interface AmenityDefinitionRepository extends ListCrudRepository<AmenityDefinition, UUID> {

    /**
     * Finds one term within a vocabulary version.
     *
     * <p>Spring derives {@code WHERE amenity_vocabulary_id = ? AND amenity_key = ?}, matching
     * {@code uk_amenity_definitions_key}.</p>
     *
     * @param amenityVocabularyId vocabulary version to look in
     * @param amenityKey stable, language-free key
     * @return the term when that version defines it
     */
    Optional<AmenityDefinition> findByAmenityVocabularyIdAndAmenityKey(
            UUID amenityVocabularyId,
            String amenityKey);

    /**
     * Returns a vocabulary version's terms grouped for presentation.
     *
     * <p>Spring derives {@code WHERE amenity_vocabulary_id = ? ORDER BY category ASC, sort_order
     * ASC}, matching {@code idx_amenity_definitions_category}. The ordering is the one a host sees
     * when filling in their listing.</p>
     *
     * @param amenityVocabularyId vocabulary version to list
     * @return possibly empty list of terms in presentation order
     */
    List<AmenityDefinition> findAllByAmenityVocabularyIdOrderByCategoryAscSortOrderAsc(
            UUID amenityVocabularyId);

    /**
     * Returns the terms guests may filter search results on.
     *
     * <p>Spring derives {@code WHERE amenity_vocabulary_id = ? AND is_searchable = true}. Not every
     * term is worth a filter, and offering one for a term almost every property has produces a filter
     * that never narrows anything.</p>
     *
     * @param amenityVocabularyId vocabulary version to list
     * @return possibly empty list of searchable terms
     */
    List<AmenityDefinition> findAllByAmenityVocabularyIdAndIsSearchableTrue(
            UUID amenityVocabularyId);
}
