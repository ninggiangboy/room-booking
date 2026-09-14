package dev.ngb.backend.trust.internal.repository.decision;

import dev.ngb.backend.trust.internal.model.decision.RiskPolicyApproval;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.trust.internal.model.decision.RiskPolicyApproval;


/**
 * Reads who signed off on a policy version.
 *
 * <p>The activation guard counts these in the database, so this repository is for showing the trail
 * rather than for deciding whether activation may proceed.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_policy_approvals}.</p>
 */
public interface RiskPolicyApprovalRepository extends ListCrudRepository<RiskPolicyApproval, UUID> {

    /**
     * Reads the approvals on one policy version.
     *
     * <pre>{@code
     * SELECT * FROM risk_policy_approvals WHERE risk_policy_id = :riskPolicyId
     * }</pre>
     *
     * @param riskPolicyId policy version
     * @return possibly empty list
     */
    List<RiskPolicyApproval> findByRiskPolicyId(UUID riskPolicyId);
}
