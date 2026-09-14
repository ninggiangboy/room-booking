package dev.ngb.backend.review.internal.repository.record_;

import dev.ngb.backend.review.internal.model.record_.ReviewRevision;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.review.internal.model.record_.ReviewRevision;


/**
 * Reads what reviews actually said.
 *
 * <p>Insert-only by trigger, so this interface reads and appends. Every derived row -- translation,
 * extraction, publication -- points at a revision rather than at the review, because that is what
 * keeps a decision tied to the text it judged.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_revisions}.</p>
 */
public interface ReviewRevisionRepository extends ListCrudRepository<ReviewRevision, UUID> {

    /**
     * Lists a review's revisions, newest first.
     *
     * <p>Spring derives {@code WHERE review_record_id = ? ORDER BY revision_number DESC}.</p>
     *
     * @param reviewRecordId review
     * @return possibly empty list, newest revision first
     */
    List<ReviewRevision> findByReviewRecordIdOrderByRevisionNumberDesc(UUID reviewRecordId);

    /**
     * Finds the revision an idempotent resubmission already created.
     *
     * <p>Spring derives {@code WHERE review_record_id = ? AND client_submission_id = ?}, matching
     * {@code uk_review_revisions_client_submission}. A retry returns the original resource.</p>
     *
     * @param reviewRecordId review
     * @param clientSubmissionId client key for the submission
     * @return the existing revision, when the key was used before
     */
    Optional<ReviewRevision> findByReviewRecordIdAndClientSubmissionId(UUID reviewRecordId,
            String clientSubmissionId);

    /**
     * Finds one numbered revision of a review.
     *
     * <p>Spring derives {@code WHERE review_record_id = ? AND revision_number = ?}, matching
     * {@code uk_review_revisions_number}.</p>
     *
     * @param reviewRecordId review
     * @param revisionNumber position in its history
     * @return the revision, when it exists
     */
    Optional<ReviewRevision> findByReviewRecordIdAndRevisionNumber(UUID reviewRecordId,
            int revisionNumber);
}
