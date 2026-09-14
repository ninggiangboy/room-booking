package dev.ngb.backend.trust.internal.repository.intervention;

import dev.ngb.backend.trust.internal.model.intervention.RiskAppeal;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads appeals.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_appeals}.</p>
 */
public interface RiskAppealRepository extends ListCrudRepository<RiskAppeal, UUID> {

    /**
     * Reads the appeals against one restriction.
     *
     * <pre>{@code
     * SELECT * FROM risk_appeals WHERE risk_restriction_id = :riskRestrictionId ORDER BY appeal_round
     * }</pre>
     *
     * @param riskRestrictionId the restriction
     * @return possibly empty list, earliest round first
     */
    List<RiskAppeal> findByRiskRestrictionIdOrderByAppealRound(UUID riskRestrictionId);

    /**
     * Reads the appeals against one decision.
     *
     * <pre>{@code
     * SELECT * FROM risk_appeals WHERE risk_decision_id = :riskDecisionId ORDER BY appeal_round
     * }</pre>
     *
     * @param riskDecisionId the decision
     * @return possibly empty list, earliest round first
     */
    List<RiskAppeal> findByRiskDecisionIdOrderByAppealRound(UUID riskDecisionId);

    /**
     * Lists appeals still open against their deadline.
     *
     * <pre>{@code
     * SELECT * FROM risk_appeals
     * WHERE state IN ('SUBMITTED', 'ACKNOWLEDGED', 'IN_REVIEW') AND deadline_at <= :at
     * ORDER BY deadline_at
     * }</pre>
     *
     * @param at instant to treat as now
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT *
            FROM risk_appeals
            WHERE state IN ('SUBMITTED', 'ACKNOWLEDGED', 'IN_REVIEW') AND deadline_at <= :at
            ORDER BY deadline_at
            """)
    List<RiskAppeal> findOverdue(@Param("at") Instant at);

    /**
     * Reads an appellant's own appeals.
     *
     * <pre>{@code
     * SELECT * FROM risk_appeals WHERE appellant_subject_id = :appellantSubjectId
     * ORDER BY submitted_at DESC
     * }</pre>
     *
     * @param appellantSubjectId the appellant
     * @return possibly empty list, most recent first
     */
    List<RiskAppeal> findByAppellantSubjectIdOrderBySubmittedAtDesc(UUID appellantSubjectId);
}
