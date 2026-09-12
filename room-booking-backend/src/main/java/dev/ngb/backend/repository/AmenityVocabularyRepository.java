package dev.ngb.backend.repository;

import dev.ngb.backend.model.AmenityVocabulary;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads versions of the controlled amenity vocabulary.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select and insert SQL
 * for {@code amenity_vocabularies}.</p>
 */
public interface AmenityVocabularyRepository extends ListCrudRepository<AmenityVocabulary, UUID> {

    /**
     * Resolves the vocabulary version in force at an instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM amenity_vocabularies
     * WHERE vocabulary_key = :vocabularyKey
     *   AND status = 'ACTIVE'
     *   AND effective_from <= :decisionInstant
     *   AND (effective_until IS NULL OR effective_until > :decisionInstant)
     * ORDER BY vocabulary_version DESC
     * LIMIT 1
     * }</pre>
     *
     * <p>The caller binds its own instant so a historical booking resolves the vocabulary as it stood
     * then. Renaming a term must not change what a guest booked last year.</p>
     *
     * @param vocabularyKey stable vocabulary key
     * @param decisionInstant instant the vocabulary should be resolved as of
     * @return the version in force, when one is active
     */
    @Query("""
            SELECT *
            FROM amenity_vocabularies
            WHERE vocabulary_key = :vocabularyKey
              AND status = 'ACTIVE'
              AND effective_from <= :decisionInstant
              AND (effective_until IS NULL OR effective_until > :decisionInstant)
            ORDER BY vocabulary_version DESC
            LIMIT 1
            """)
    Optional<AmenityVocabulary> findInForce(
            @Param("vocabularyKey") String vocabularyKey,
            @Param("decisionInstant") Instant decisionInstant);

    /**
     * Returns every version of one vocabulary, newest first.
     *
     * <p>Spring derives {@code WHERE vocabulary_key = ? ORDER BY vocabulary_version DESC}, including
     * retired versions, which historical references still resolve against.</p>
     *
     * @param vocabularyKey stable vocabulary key
     * @return possibly empty list of versions, newest first
     */
    List<AmenityVocabulary> findAllByVocabularyKeyOrderByVocabularyVersionDesc(
            String vocabularyKey);
}
