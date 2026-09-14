package dev.ngb.backend.trust.internal.repository.decision;

import dev.ngb.backend.trust.internal.model.decision.RiskDecisionEnforcement;
import dev.ngb.backend.trust.internal.model.RiskEnforcementDomain;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.trust.internal.model.RiskEnforcementDomain;
import dev.ngb.backend.trust.internal.model.decision.RiskDecisionEnforcement;


/**
 * Reads which domain commands honoured which decisions.
 *
 * <p>This is the table that answers whether a refusal was enforced or merely recorded, so the
 * divergence query below is the one an operator reads when the two halves disagree.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_decision_enforcements}.</p>
 */
public interface RiskDecisionEnforcementRepository extends ListCrudRepository<RiskDecisionEnforcement, UUID> {

    /**
     * Reads how one decision was enforced.
     *
     * <pre>{@code
     * SELECT * FROM risk_decision_enforcements WHERE risk_decision_id = :riskDecisionId
     * }</pre>
     *
     * @param riskDecisionId the decision
     * @return possibly empty list
     */
    List<RiskDecisionEnforcement> findByRiskDecisionId(UUID riskDecisionId);

    /**
     * Finds the decision one domain command enforced.
     *
     * <pre>{@code
     * SELECT * FROM risk_decision_enforcements
     * WHERE enforcing_domain = :enforcingDomain
     *   AND command_type = :commandType
     *   AND command_id = :commandId
     * }</pre>
     *
     * <p>Matches {@code uk_risk_decision_enforcements_command}: one command enforces one decision.</p>
     *
     * @param enforcingDomain domain that ran the command
     * @param commandType which command
     * @param commandId which instance of it
     * @return the enforcement record, when the command ran
     */
    Optional<RiskDecisionEnforcement> findByEnforcingDomainAndCommandTypeAndCommandId(
            RiskEnforcementDomain enforcingDomain, String commandType, UUID commandId);

    /**
     * Reads the divergences in a window.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_decision_enforcements
     * WHERE enforcement_result = 'DIVERGED' AND enforced_at >= :from AND enforced_at < :to
     * ORDER BY enforced_at
     * }</pre>
     *
     * <p>Each carries a stated reason, because the constraint refuses a silent one.</p>
     *
     * @param from inclusive start
     * @param to exclusive end
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT *
            FROM risk_decision_enforcements
            WHERE enforcement_result = 'DIVERGED' AND enforced_at >= :from AND enforced_at < :to
            ORDER BY enforced_at
            """)
    List<RiskDecisionEnforcement> findDivergences(@Param("from") Instant from, @Param("to") Instant to);
}
