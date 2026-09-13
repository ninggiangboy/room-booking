package dev.ngb.backend.repository;

import dev.ngb.backend.model.RiskDecisionRuleHit;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads which rules fired, in order.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_decision_rule_hits}.</p>
 */
public interface RiskDecisionRuleHitRepository extends ListCrudRepository<RiskDecisionRuleHit, UUID> {

    /**
     * Reads the rule hits behind one decision.
     *
     * <pre>{@code
     * SELECT * FROM risk_decision_rule_hits WHERE risk_decision_id = :riskDecisionId
     * ORDER BY hit_sequence
     * }</pre>
     *
     * @param riskDecisionId the decision
     * @return possibly empty list, in evaluation order
     */
    List<RiskDecisionRuleHit> findByRiskDecisionIdOrderByHitSequence(UUID riskDecisionId);
}
