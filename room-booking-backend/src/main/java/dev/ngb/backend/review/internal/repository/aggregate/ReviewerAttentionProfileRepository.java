package dev.ngb.backend.review.internal.repository.aggregate;

import dev.ngb.backend.review.internal.model.aggregate.ReviewerAttentionProfile;
import dev.ngb.backend.review.internal.model.aggregate.AttentionProfileStatus;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what individual reviewers repeatedly notice.
 *
 * <p>Personal data about a person. A withdrawn or opted-out profile is never current, which is a
 * check constraint rather than a filter a consumer has to remember.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code reviewer_attention_profiles}.</p>
 */
public interface ReviewerAttentionProfileRepository extends ListCrudRepository<ReviewerAttentionProfile, UUID> {

    /**
     * Finds the profile in force for a reviewer and purpose.
     *
     * <p>Spring derives {@code WHERE reviewer_account_holder_id = ? AND purpose_code = ? AND status = ?},
     * matching {@code uk_reviewer_attention_profiles_current} when the status is {@code CURRENT}.</p>
     *
     * @param reviewerAccountHolderId reviewer
     * @param purposeCode what the profile may be used for
     * @param status status to match, normally {@code CURRENT}
     * @return the current profile, when one exists
     */
    Optional<ReviewerAttentionProfile> findByReviewerAccountHolderIdAndPurposeCodeAndStatus(
            UUID reviewerAccountHolderId, String purposeCode, AttentionProfileStatus status);

    /**
     * Lists every profile held about one person.
     *
     * <p>Spring derives {@code WHERE reviewer_account_holder_id = ?}. What a subject-access or deletion
     * request needs to enumerate.</p>
     *
     * @param reviewerAccountHolderId reviewer
     * @return possibly empty list
     */
    List<ReviewerAttentionProfile> findByReviewerAccountHolderId(UUID reviewerAccountHolderId);
}
