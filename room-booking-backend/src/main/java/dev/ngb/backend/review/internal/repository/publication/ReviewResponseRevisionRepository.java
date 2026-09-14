package dev.ngb.backend.review.internal.repository.publication;

import dev.ngb.backend.review.internal.model.publication.ReviewResponseRevision;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.review.internal.model.publication.ReviewResponseRevision;


/**
 * Reads the text of host responses.
 *
 * <p>Insert-only by trigger.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_response_revisions}.</p>
 */
public interface ReviewResponseRevisionRepository extends ListCrudRepository<ReviewResponseRevision, UUID> {

    /**
     * Lists a response's revisions, newest first.
     *
     * <p>Spring derives {@code WHERE review_response_id = ? ORDER BY revision_number DESC}.</p>
     *
     * @param reviewResponseId response
     * @return possibly empty list, newest first
     */
    List<ReviewResponseRevision> findByReviewResponseIdOrderByRevisionNumberDesc(UUID reviewResponseId);
}
