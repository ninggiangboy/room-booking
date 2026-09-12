package dev.ngb.backend.repository;

import dev.ngb.backend.model.ReviewPublicAggregate;
import dev.ngb.backend.model.AggregateSubjectType;
import dev.ngb.backend.model.ReviewDirection;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the transparent public averages.
 *
 * <p>What comes back carries the sum, the count and the distribution it was computed from, so a
 * caller can show the arithmetic rather than asking the reader to trust a number.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_public_aggregates}.</p>
 */
public interface ReviewPublicAggregateRepository extends ListCrudRepository<ReviewPublicAggregate, UUID> {

    /**
     * Finds one aggregate by its key.
     *
     * <p>Spring derives {@code WHERE subject_type = ? AND subject_id = ? AND direction = ? AND
     * dimension_code = ? AND aggregate_rule_version = ?}, matching
     * {@code uk_review_public_aggregates_key}.</p>
     *
     * @param subjectType what it counts for
     * @param subjectId which one
     * @param direction which direction of review
     * @param dimensionCode overall, or a named category
     * @param aggregateRuleVersion rule version
     * @return the aggregate, when it exists
     */
    Optional<ReviewPublicAggregate> findBySubjectTypeAndSubjectIdAndDirectionAndDimensionCodeAndAggregateRuleVersion(
            AggregateSubjectType subjectType, UUID subjectId, ReviewDirection direction,
            String dimensionCode, int aggregateRuleVersion);

    /**
     * Lists every dimension counted for one subject.
     *
     * <p>Spring derives {@code WHERE subject_type = ? AND subject_id = ?}, matching
     * {@code idx_review_public_aggregates_subject}.</p>
     *
     * @param subjectType what the aggregates count for
     * @param subjectId which one
     * @return possibly empty list
     */
    List<ReviewPublicAggregate> findBySubjectTypeAndSubjectId(AggregateSubjectType subjectType,
            UUID subjectId);

    /**
     * Lists aggregates known to be behind their inputs.
     *
     * <p>Spring derives {@code WHERE is_stale = ? ORDER BY input_watermark}, matching
     * {@code idx_review_public_aggregates_stale}.</p>
     *
     * @param isStale staleness flag to match
     * @return possibly empty list, furthest behind first
     */
    List<ReviewPublicAggregate> findByIsStaleOrderByInputWatermark(boolean isStale);
}
