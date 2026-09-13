package dev.ngb.backend.repository;

import dev.ngb.backend.model.ChangeRequestApproval;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads who agreed to what.
 *
 * <p>Append-only, and each row carries the digest of the value it approved, so an approval of a
 * proposal that has since moved is detectable rather than inherited.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code change_request_approvals}.</p>
 */
public interface ChangeRequestApprovalRepository extends ListCrudRepository<ChangeRequestApproval, UUID> {

    /**
     * Lists the sign-offs on one request.
     *
     * @param changeRequestId the request
     * @return possibly empty list
     */
    List<ChangeRequestApproval> findByChangeRequestId(UUID changeRequestId);

    /**
     * Finds one role sign-off on one request, which is what the progression guard looks for.
     *
     * @param changeRequestId the request
     * @param approvalRole the approval role
     * @return the decision, when that role has made one
     */
    Optional<ChangeRequestApproval> findByChangeRequestIdAndApprovalRole(UUID changeRequestId,
            String approvalRole);

    /**
     * Lists what one approver has decided, newest first, which is what an approval-load review and
     * an investigation both read.
     *
     * @param approverId the approver
     * @return possibly empty list, most recent first
     */
    List<ChangeRequestApproval> findByApproverIdOrderByDecidedAtDesc(UUID approverId);

    /**
     * Counts the agreements on one request, which is what a console shows next to the roles still
     * outstanding.
     *
     * <pre>{@code
     * SELECT count(*) FROM change_request_approvals
     * WHERE change_request_id = :changeRequestId AND decision = 'APPROVED'
     * }</pre>
     *
     * @param changeRequestId the request
     * @return how many roles have agreed
     */
    @Query("""
            SELECT count(*) FROM change_request_approvals
            WHERE change_request_id = :changeRequestId AND decision = 'APPROVED'
            """)
    long countApprovals(@Param("changeRequestId") UUID changeRequestId);

    /**
     * Lists the approvals whose digest no longer matches anything, which is the shape a proposal
     * edited after sign-off would leave behind if the freeze were ever lifted.
     *
     * <pre>{@code
     * SELECT a.* FROM change_request_approvals a
     * JOIN change_requests r ON r.id = a.change_request_id
     * WHERE r.current_value_digest IS NOT NULL AND a.approved_digest <> r.current_value_digest
     * ORDER BY a.decided_at DESC
     * }</pre>
     *
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT a.* FROM change_request_approvals a
            JOIN change_requests r ON r.id = a.change_request_id
            WHERE r.current_value_digest IS NOT NULL AND a.approved_digest <> r.current_value_digest
            ORDER BY a.decided_at DESC
            """)
    List<ChangeRequestApproval> findStale();
}
