package dev.ngb.backend.review.internal.repository.publication;

import dev.ngb.backend.review.internal.model.publication.ReviewAggregateContribution;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.review.internal.model.publication.ReviewAggregateContribution;


/**
 * Reads what fed the public averages.
 *
 * <p>Exists so a rebuild can be compared against what was actually counted. Frozen except for the
 * end of the inclusion interval.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_aggregate_contributions}.</p>
 */
public interface ReviewAggregateContributionRepository extends ListCrudRepository<ReviewAggregateContribution, UUID> {

    /**
     * Finds whether a publication has already been counted into an aggregate.
     *
     * <p>Spring derives {@code WHERE review_public_aggregate_id = ? AND review_publication_id = ?},
     * matching {@code uk_review_aggregate_contributions_identity}. This is what makes an incremental
     * delta idempotent.</p>
     *
     * @param reviewPublicAggregateId aggregate
     * @param reviewPublicationId publication
     * @return the contribution, when it was already counted
     */
    Optional<ReviewAggregateContribution> findByReviewPublicAggregateIdAndReviewPublicationId(
            UUID reviewPublicAggregateId, UUID reviewPublicationId);

    /**
     * Lists what is currently counted into one aggregate.
     *
     * <pre>{@code
     * SELECT *
     * FROM review_aggregate_contributions
     * WHERE review_public_aggregate_id = :reviewPublicAggregateId
     *   AND included_until IS NULL
     * }</pre>
     *
     * <p>Matches {@code idx_review_aggregate_contributions_aggregate}. Summing these must reproduce the
     * aggregate; where it does not, one of the two is wrong and the rebuild says which.</p>
     *
     * @param reviewPublicAggregateId aggregate
     * @return possibly empty list
     */
    @Query("""
            SELECT *
            FROM review_aggregate_contributions
            WHERE review_public_aggregate_id = :reviewPublicAggregateId
              AND included_until IS NULL
            """)
    List<ReviewAggregateContribution> findIncluded(
            @Param("reviewPublicAggregateId") UUID reviewPublicAggregateId);
}
