package dev.ngb.backend.review.internal.repository.record_;

import dev.ngb.backend.review.internal.model.record_.ReviewTranslation;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.UUID;

/**
 * Reads derived translations of review text.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_translations}.</p>
 */
public interface ReviewTranslationRepository extends ListCrudRepository<ReviewTranslation, UUID> {

    /**
     * Lists the translations of one revision into one locale.
     *
     * <p>Spring derives {@code WHERE review_revision_id = ? AND target_locale = ?}, matching
     * {@code idx_review_translations_revision}. Several may exist across engines and sources, so the
     * caller chooses rather than the query guessing.</p>
     *
     * @param reviewRevisionId revision
     * @param targetLocale locale wanted
     * @return possibly empty list
     */
    List<ReviewTranslation> findByReviewRevisionIdAndTargetLocale(UUID reviewRevisionId,
            String targetLocale);

    /**
     * Lists every translation of one revision.
     *
     * <p>Spring derives {@code WHERE review_revision_id = ?}.</p>
     *
     * @param reviewRevisionId revision
     * @return possibly empty list
     */
    List<ReviewTranslation> findByReviewRevisionId(UUID reviewRevisionId);
}
