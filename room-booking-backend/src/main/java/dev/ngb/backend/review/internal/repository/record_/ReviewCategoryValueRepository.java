package dev.ngb.backend.review.internal.repository.record_;

import dev.ngb.backend.review.internal.model.record_.ReviewCategoryValue;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.review.internal.model.record_.ReviewCategoryValue;


/**
 * Reads the per-category ratings of a revision.
 *
 * <p>Written with their revision and never appended to a submitted one, so a read here is a read of
 * what the reviewer actually submitted.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_category_values}.</p>
 */
public interface ReviewCategoryValueRepository extends ListCrudRepository<ReviewCategoryValue, UUID> {

    /**
     * Lists the category ratings of one revision.
     *
     * <p>Spring derives {@code WHERE review_revision_id = ?}.</p>
     *
     * @param reviewRevisionId revision
     * @return possibly empty list
     */
    List<ReviewCategoryValue> findByReviewRevisionId(UUID reviewRevisionId);

    /**
     * Finds one category rating of a revision.
     *
     * <p>Spring derives {@code WHERE review_revision_id = ? AND category_code = ?}, matching
     * {@code uk_review_category_values_category}.</p>
     *
     * @param reviewRevisionId revision
     * @param categoryCode category
     * @return the rating, when the reviewer gave one
     */
    Optional<ReviewCategoryValue> findByReviewRevisionIdAndCategoryCode(UUID reviewRevisionId,
            String categoryCode);
}
