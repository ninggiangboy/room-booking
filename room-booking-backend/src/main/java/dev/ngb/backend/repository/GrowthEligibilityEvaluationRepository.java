package dev.ngb.backend.repository;

import dev.ngb.backend.model.GrowthEligibilityEvaluation;
import dev.ngb.backend.model.GrowthEligibilityOutcome;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the recorded decisions about who qualified for what, and why.
 *
 * <p>Rows are append-only, so a decision taken months ago still carries the rules it was taken
 * under and the sentence the guest was shown. That is the whole point of storing it.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code growth_eligibility_evaluations}.</p>
 */
public interface GrowthEligibilityEvaluationRepository extends ListCrudRepository<GrowthEligibilityEvaluation, UUID> {

    /**
     * Lists the decisions taken about one person, newest first.
     *
     * @param accountHolderId the person
     * @return possibly empty list, most recent first
     */
    List<GrowthEligibilityEvaluation> findByAccountHolderIdOrderByEvaluatedAtDesc(
            UUID accountHolderId);

    /**
     * Finds the most recent decision about one person under one set of terms, which is the one to
     * answer a "why was I not eligible" question with.
     *
     * <pre>{@code
     * SELECT * FROM growth_eligibility_evaluations
     * WHERE growth_program_version_id = :growthProgramVersionId
     *   AND account_holder_id = :accountHolderId
     * ORDER BY evaluated_at DESC
     * LIMIT 1
     * }</pre>
     *
     * @param growthProgramVersionId the terms it was taken under
     * @param accountHolderId the person
     * @return the most recent decision, when there is one
     */
    @Query("""
            SELECT * FROM growth_eligibility_evaluations
            WHERE growth_program_version_id = :growthProgramVersionId
              AND account_holder_id = :accountHolderId
            ORDER BY evaluated_at DESC
            LIMIT 1
            """)
    Optional<GrowthEligibilityEvaluation> findLatest(
            @Param("growthProgramVersionId") UUID growthProgramVersionId,
            @Param("accountHolderId") UUID accountHolderId);

    /**
     * Counts how each outcome was reached under one set of terms, which is how a programme that
     * is refusing almost everybody becomes visible before anybody complains.
     *
     * <pre>{@code
     * SELECT outcome, count(*) AS decision_count
     * FROM growth_eligibility_evaluations
     * WHERE growth_program_version_id = :growthProgramVersionId
     * GROUP BY outcome
     * ORDER BY decision_count DESC
     * }</pre>
     *
     * @param growthProgramVersionId the terms
     * @return one row per outcome, commonest first
     */
    @Query("""
            SELECT outcome, count(*) AS decision_count
            FROM growth_eligibility_evaluations
            WHERE growth_program_version_id = :growthProgramVersionId
            GROUP BY outcome
            ORDER BY decision_count DESC
            """)
    List<OutcomeCount> countOutcomes(
            @Param("growthProgramVersionId") UUID growthProgramVersionId);

    /**
     * How many decisions reached one outcome.
     *
     * @param outcome what the rules decided
     * @param decisionCount how many decisions reached it
     */
    record OutcomeCount(GrowthEligibilityOutcome outcome, long decisionCount) {}
}
