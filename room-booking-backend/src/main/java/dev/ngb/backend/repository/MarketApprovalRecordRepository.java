package dev.ngb.backend.repository;

import dev.ngb.backend.model.ApprovalSubjectType;
import dev.ngb.backend.model.MarketApprovalRecord;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Appends and reads the approval trail for configuration changes.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select and insert SQL
 * for {@code market_approval_records}. Records are superseded rather than edited.</p>
 */
public interface MarketApprovalRecordRepository
        extends ListCrudRepository<MarketApprovalRecord, UUID> {

    /**
     * Returns the decisions taken about one piece of configuration, most recent first.
     *
     * <p>Spring derives {@code WHERE subject_type = ? AND subject_id = ?} and descending ordering
     * from the {@code OrderByDecidedAtDesc} suffix:</p>
     *
     * <pre>{@code
     * SELECT ...
     * FROM market_approval_records
     * WHERE subject_type = ?
     *   AND subject_id = ?
     * ORDER BY decided_at DESC
     * }</pre>
     *
     * <p>The list includes rejections and revocations, because a trail showing only approvals could
     * not demonstrate that a proposal was considered and refused.</p>
     *
     * @param subjectType kind of configuration
     * @param subjectId identifier of that configuration row
     * @return possibly empty decision history, most recent first
     */
    List<MarketApprovalRecord> findAllBySubjectTypeAndSubjectIdOrderByDecidedAtDesc(
            ApprovalSubjectType subjectType,
            UUID subjectId);

    /**
     * Returns every decision one operator has taken, most recent first.
     *
     * <p>Spring derives {@code WHERE approver_id = ? ORDER BY decided_at DESC}. Used when reviewing
     * an approver's activity.</p>
     *
     * @param approverId operator whose decisions are being reviewed
     * @return possibly empty decision history
     */
    List<MarketApprovalRecord> findAllByApproverIdOrderByDecidedAtDesc(UUID approverId);
}
