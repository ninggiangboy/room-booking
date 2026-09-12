package dev.ngb.backend.repository;

import dev.ngb.backend.model.ReviewerAttentionValue;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads the per-aspect values of a reviewer attention profile.
 *
 * <p>This minimized view is what may be shared with discovery. The review text it came from is not.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code reviewer_attention_values}.</p>
 */
public interface ReviewerAttentionValueRepository extends ListCrudRepository<ReviewerAttentionValue, UUID> {

    /**
     * Lists what a reviewer attends to, strongest first.
     *
     * <p>Spring derives {@code WHERE reviewer_attention_profile_id = ? ORDER BY attention_score DESC}.
     * The stay and mention counts come back with each row, so a consumer can see how thin the evidence
     * is before acting on it.</p>
     *
     * @param reviewerAttentionProfileId profile
     * @return possibly empty list, strongest attention first
     */
    List<ReviewerAttentionValue> findByReviewerAttentionProfileIdOrderByAttentionScoreDesc(
            UUID reviewerAttentionProfileId);
}
