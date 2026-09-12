package dev.ngb.backend.repository;

import dev.ngb.backend.model.ReviewHelpfulVote;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads helpfulness votes.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_helpful_votes}.</p>
 */
public interface ReviewHelpfulVoteRepository extends ListCrudRepository<ReviewHelpfulVote, UUID> {

    /**
     * Finds a person's vote on a review.
     *
     * <p>Spring derives {@code WHERE review_record_id = ? AND voter_account_holder_id = ?}, matching
     * {@code uk_review_helpful_votes_voter}.</p>
     *
     * @param reviewRecordId review
     * @param voterAccountHolderId voter
     * @return the vote, when they have voted
     */
    Optional<ReviewHelpfulVote> findByReviewRecordIdAndVoterAccountHolderId(UUID reviewRecordId,
            UUID voterAccountHolderId);

    /**
     * Counts the votes that still stand for a review.
     *
     * <pre>{@code
     * SELECT count(*)
     * FROM review_helpful_votes
     * WHERE review_record_id = :reviewRecordId AND state = 'ACTIVE'
     * }</pre>
     *
     * <p>Matches {@code idx_review_helpful_votes_record}.</p>
     *
     * @param reviewRecordId review
     * @return number of active votes
     */
    @Query("""
            SELECT count(*)
            FROM review_helpful_votes
            WHERE review_record_id = :reviewRecordId AND state = 'ACTIVE'
            """)
    long countActive(@Param("reviewRecordId") UUID reviewRecordId);
}
