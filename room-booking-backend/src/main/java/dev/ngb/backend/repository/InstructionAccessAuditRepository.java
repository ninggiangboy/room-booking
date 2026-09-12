package dev.ngb.backend.repository;

import dev.ngb.backend.model.InstructionAccessAudit;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads the record of who retrieved which instruction band.
 *
 * <p>Append-only by trigger, so this interface reads and appends and never updates. The secret-band
 * query is where a compromised-access investigation starts.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code instruction_access_audit}.</p>
 */
public interface InstructionAccessAuditRepository extends ListCrudRepository<InstructionAccessAudit, UUID> {

    /**
     * Lists retrievals of one instruction version, newest first.
     *
     * <p>Spring derives {@code WHERE instruction_set_id = ? ORDER BY occurred_at DESC}.</p>
     *
     * @param instructionSetId version retrieved
     * @return possibly empty list, most recent first
     */
    List<InstructionAccessAudit> findByInstructionSetIdOrderByOccurredAtDesc(UUID instructionSetId);

    /**
     * Lists secret-band retrievals for a stay since an instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM instruction_access_audit
     * WHERE operational_stay_id = :operationalStayId
     *   AND requested_field_class = 'SECRET'
     *   AND occurred_at >= :since
     * ORDER BY occurred_at DESC
     * }</pre>
     *
     * <p>Answers the question a compromised-access runbook asks first: who has seen the code, and
     * when.</p>
     *
     * @param operationalStayId stay
     * @param since earliest instant of interest
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT *
            FROM instruction_access_audit
            WHERE operational_stay_id = :operationalStayId
              AND requested_field_class = 'SECRET'
              AND occurred_at >= :since
            ORDER BY occurred_at DESC
            """)
    List<InstructionAccessAudit> findSecretRetrievals(@Param("operationalStayId") UUID operationalStayId,
            @Param("since") Instant since);
}
