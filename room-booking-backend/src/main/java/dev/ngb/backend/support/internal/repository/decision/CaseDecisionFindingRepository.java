package dev.ngb.backend.support.internal.repository.decision;

import dev.ngb.backend.support.internal.model.decision.CaseDecisionFinding;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.UUID;

/**
 * Reads which findings a decision rested on.
 *
 * <p>Append-only in the database.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_decision_findings}.</p>
 */
public interface CaseDecisionFindingRepository extends ListCrudRepository<CaseDecisionFinding, UUID> {

    /**
     * Lists the findings a decision cited.
     *
     * @param caseDecisionId decision
     * @return possibly empty list
     */
    List<CaseDecisionFinding> findByCaseDecisionId(UUID caseDecisionId);

    /**
     * Lists the decisions that cited one finding, so a superseded finding can be traced forward.
     *
     * @param caseFindingId finding
     * @return possibly empty list
     */
    List<CaseDecisionFinding> findByCaseFindingId(UUID caseFindingId);
}
