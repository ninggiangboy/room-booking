package dev.ngb.backend.support.internal.repository.decision;

import dev.ngb.backend.support.internal.model.decision.CaseAppeal;
import dev.ngb.backend.support.internal.model.decision.CaseAppealState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.support.internal.model.decision.CaseAppeal;
import dev.ngb.backend.support.internal.model.decision.CaseAppealState;


/**
 * Reads the appeals brought against decisions.
 *
 * <p>An open appeal blocks closure outright, which is why the live lookup exists separately from the
 * general listing.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_appeals}.</p>
 */
public interface CaseAppealRepository extends ListCrudRepository<CaseAppeal, UUID> {

    /**
     * Finds the live appeal against one decision.
     *
     * <pre>{@code
     * SELECT * FROM case_appeals
     * WHERE challenged_decision_id = :challengedDecisionId
     *   AND state NOT IN ('CLOSED', 'WITHDRAWN', 'INELIGIBLE')
     * }</pre>
     *
     * <p>Matches {@code uk_case_appeals_live}, so at most one row can come back.</p>
     *
     * @param challengedDecisionId decision under appeal
     * @return the live appeal, when one is open
     */
    @Query("""
            SELECT * FROM case_appeals
            WHERE challenged_decision_id = :challengedDecisionId
              AND state NOT IN ('CLOSED', 'WITHDRAWN', 'INELIGIBLE')
            """)
    Optional<CaseAppeal> findLive(@Param("challengedDecisionId") UUID challengedDecisionId);

    /**
     * Lists the appeals on a case.
     *
     * @param supportCaseId case
     * @param state appeal state
     * @return possibly empty list
     */
    List<CaseAppeal> findBySupportCaseIdAndState(UUID supportCaseId, CaseAppealState state);

    /**
     * Lists the appeals assigned to one reviewer.
     *
     * @param reviewerAccountHolderId reviewer
     * @param state appeal state
     * @return possibly empty list
     */
    List<CaseAppeal> findByReviewerAccountHolderIdAndState(UUID reviewerAccountHolderId,
            CaseAppealState state);
}
