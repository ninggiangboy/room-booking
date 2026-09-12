package dev.ngb.backend.repository;

import dev.ngb.backend.model.FinanceAdjustmentApproval;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads who approved which correction.
 *
 * <p>Append-only and unique per approver, so a decision cannot be amended and one person cannot satisfy
 * a two-approval threshold alone.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code finance_adjustment_approvals}.</p>
 */
public interface FinanceAdjustmentApprovalRepository extends ListCrudRepository<FinanceAdjustmentApproval, UUID> {

    /**
     * Returns the decisions recorded against one request, oldest first.
     *
     * <p>Spring derives {@code WHERE adjustment_request_id = ? ORDER BY decided_at}, matching
     * {@code idx_finance_adjustment_approvals_request}.</p>
     *
     * @param adjustmentRequestId request whose approvals are wanted
     * @return possibly empty list of approvals
     */
    List<FinanceAdjustmentApproval> findAllByAdjustmentRequestIdOrderByDecidedAt(
            UUID adjustmentRequestId);

    /**
     * Finds one approver's decision about one request.
     *
     * <p>Spring derives {@code WHERE adjustment_request_id = ? AND approver_actor_id = ?}, matching
     * {@code uk_finance_adjustment_approvals_approver}.</p>
     *
     * @param adjustmentRequestId request being decided
     * @param approverActorId person who decided
     * @return their decision, when they have made one
     */
    Optional<FinanceAdjustmentApproval> findByAdjustmentRequestIdAndApproverActorId(
            UUID adjustmentRequestId, UUID approverActorId);
}
