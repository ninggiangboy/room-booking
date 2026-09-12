package dev.ngb.backend.repository;

import dev.ngb.backend.model.ReviewAspectMention;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads what reviews said about aspects.
 *
 * <p>Frozen except for inclusion and validation, so a mention is excluded rather than deleted and the
 * record of what the extractor read survives the exclusion.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_aspect_mentions}.</p>
 */
public interface ReviewAspectMentionRepository extends ListCrudRepository<ReviewAspectMention, UUID> {

    /**
     * Lists the mentions read out of one revision.
     *
     * <p>Spring derives {@code WHERE review_revision_id = ?}, matching
     * {@code idx_review_aspect_mentions_revision}.</p>
     *
     * @param reviewRevisionId revision
     * @return possibly empty list
     */
    List<ReviewAspectMention> findByReviewRevisionId(UUID reviewRevisionId);

    /**
     * Lists the mentions one run produced.
     *
     * <p>Spring derives {@code WHERE review_extraction_run_id = ?}, matching
     * {@code idx_review_aspect_mentions_run}.</p>
     *
     * @param reviewExtractionRunId run
     * @return possibly empty list
     */
    List<ReviewAspectMention> findByReviewExtractionRunId(UUID reviewExtractionRunId);

    /**
     * Lists the qualified mentions of one aspect under one vocabulary version.
     *
     * <pre>{@code
     * SELECT *
     * FROM review_aspect_mentions
     * WHERE aspect_taxonomy_version_id = :aspectTaxonomyVersionId
     *   AND aspect_code = :aspectCode
     *   AND aspect_target = :aspectTarget
     *   AND inclusion_state = 'QUALIFIED'
     * }</pre>
     *
     * <p>Matches {@code idx_review_aspect_mentions_aspect}. What a profile is computed from.</p>
     *
     * @param aspectTaxonomyVersionId vocabulary version
     * @param aspectCode aspect
     * @param aspectTarget what the mentions are about
     * @return possibly empty list
     */
    @Query("""
            SELECT *
            FROM review_aspect_mentions
            WHERE aspect_taxonomy_version_id = :aspectTaxonomyVersionId
              AND aspect_code = :aspectCode
              AND aspect_target = :aspectTarget
              AND inclusion_state = 'QUALIFIED'
            """)
    List<ReviewAspectMention> findQualified(
            @Param("aspectTaxonomyVersionId") UUID aspectTaxonomyVersionId,
            @Param("aspectCode") String aspectCode, @Param("aspectTarget") String aspectTarget);
}
