package dev.ngb.backend.support.internal.repository.case_;

import dev.ngb.backend.support.internal.model.case_.CaseQualityReview;
import dev.ngb.backend.support.internal.model.case_.QualitySampleCohort;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.support.internal.model.case_.CaseQualityReview;
import dev.ngb.backend.support.internal.model.case_.QualitySampleCohort;


/**
 * Reads the sampled quality reviews.
 *
 * <p>Sampling is stratified, so the cohort is part of the identity: reviewing one decision again under
 * a different cohort is legitimate, reviewing it twice under the same one is not.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_quality_reviews}.</p>
 */
public interface CaseQualityReviewRepository extends ListCrudRepository<CaseQualityReview, UUID> {

    /**
     * Lists the reviews taken on a case.
     *
     * @param supportCaseId case
     * @return possibly empty list
     */
    List<CaseQualityReview> findBySupportCaseId(UUID supportCaseId);

    /**
     * Finds the review of one decision in a cohort.
     *
     * @param caseDecisionId decision
     * @param sampleCohort stratum it was drawn from
     * @return the review, when one was taken
     */
    Optional<CaseQualityReview> findByCaseDecisionIdAndSampleCohort(UUID caseDecisionId,
            QualitySampleCohort sampleCohort);

    /**
     * Claims reviews whose correction is overdue.
     *
     * <pre>{@code
     * SELECT *
     * FROM case_quality_reviews
     * WHERE correction_due_at IS NOT NULL AND correction_due_at <= :at
     * ORDER BY correction_due_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. A coaching or policy correction nobody chases is a review that
     * changed nothing.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum reviews to claim
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT *
            FROM case_quality_reviews
            WHERE correction_due_at IS NOT NULL AND correction_due_at <= :at
            ORDER BY correction_due_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<CaseQualityReview> claimOverdueCorrections(@Param("at") Instant at,
            @Param("batchSize") int batchSize);
}
