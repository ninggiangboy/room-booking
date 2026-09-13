package dev.ngb.backend.repository;

import dev.ngb.backend.model.CaseDecisionApproval;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads the approvals standing against a decision.
 *
 * <p>Append-only in the database: an approval is invalidated, never edited. The live lookup is what
 * the effective-decision guard reads, and it ignores approvals of a digest the decision no longer
 * carries.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_decision_approvals}.</p>
 */
public interface CaseDecisionApprovalRepository extends ListCrudRepository<CaseDecisionApproval, UUID> {

    /**
     * Lists the approvals still standing against a decision.
     *
     * <pre>{@code
     * SELECT * FROM case_decision_approvals
     * WHERE case_decision_id = :caseDecisionId AND invalidated_at IS NULL
     * ORDER BY decided_at
     * }</pre>
     *
     * @param caseDecisionId decision
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM case_decision_approvals
            WHERE case_decision_id = :caseDecisionId AND invalidated_at IS NULL
            ORDER BY decided_at
            """)
    List<CaseDecisionApproval> findStanding(@Param("caseDecisionId") UUID caseDecisionId);

    /**
     * Lists what one person has approved, for conflict and quality review.
     *
     * @param approverAccountHolderId approver
     * @return possibly empty list, most recent first
     */
    List<CaseDecisionApproval> findByApproverAccountHolderIdOrderByDecidedAtDesc(
            UUID approverAccountHolderId);
}
