package dev.ngb.backend.admin.internal.repository.command;

import dev.ngb.backend.admin.internal.model.command.OperationalCommandApproval;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads who agreed to an operator command.
 *
 * <p>Append-only, never by the operator issuing it, and carrying both the parameter digest and the
 * amount the approval covers, so an approval of something smaller cannot be spent on something
 * larger.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code operational_command_approvals}.</p>
 */
public interface OperationalCommandApprovalRepository extends ListCrudRepository<OperationalCommandApproval, UUID> {

    /**
     * Lists the sign-offs on one execution.
     *
     * @param commandExecutionId the execution
     * @return possibly empty list
     */
    List<OperationalCommandApproval> findByCommandExecutionId(UUID commandExecutionId);

    /**
     * Finds one role sign-off on one execution, which is what the dispatch guard looks for.
     *
     * @param commandExecutionId the execution
     * @param approvalRole the approval role
     * @return the decision, when that role has made one
     */
    Optional<OperationalCommandApproval> findByCommandExecutionIdAndApprovalRole(
            UUID commandExecutionId, String approvalRole);

    /**
     * Lists what one approver has decided, newest first.
     *
     * @param approverId the approver
     * @return possibly empty list, most recent first
     */
    List<OperationalCommandApproval> findByApproverIdOrderByDecidedAtDesc(UUID approverId);

    /**
     * Sums the money one approver has authorised in a window, which is the exposure a single
     * approver represents and is not visible from any one approval.
     *
     * <pre>{@code
     * SELECT coalesce(sum(approved_amount_minor), 0) FROM operational_command_approvals
     * WHERE approver_id = :approverId AND decision = 'APPROVED'
     *   AND approved_amount_minor IS NOT NULL
     *   AND decided_at >= :from AND decided_at < :to
     * }</pre>
     *
     * @param approverId the approver
     * @param from start of the window, inclusive
     * @param to end of the window, exclusive
     * @return total authorised in integer minor units, zero when nothing was
     */
    @Query("""
            SELECT coalesce(sum(approved_amount_minor), 0) FROM operational_command_approvals
            WHERE approver_id = :approverId AND decision = 'APPROVED'
              AND approved_amount_minor IS NOT NULL
              AND decided_at >= :from AND decided_at < :to
            """)
    long sumAuthorised(@Param("approverId") UUID approverId, @Param("from") Instant from,
            @Param("to") Instant to);
}
