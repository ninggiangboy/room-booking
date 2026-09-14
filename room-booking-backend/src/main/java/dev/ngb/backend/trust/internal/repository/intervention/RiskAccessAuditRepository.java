package dev.ngb.backend.trust.internal.repository.intervention;

import dev.ngb.backend.trust.internal.model.intervention.RiskAccessAudit;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.trust.internal.model.intervention.RiskAccessAudit;


/**
 * Reads who looked at protected risk evidence.
 *
 * <p>Append-only, so there is no update path. Reading this table is itself an access worth recording,
 * which is why the audit covers views and queries as well as changes.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_access_audit}.</p>
 */
public interface RiskAccessAuditRepository extends ListCrudRepository<RiskAccessAudit, UUID> {

    /**
     * Reads one actor's access history.
     *
     * <pre>{@code
     * SELECT * FROM risk_access_audit WHERE actor_account_holder_id = :actorAccountHolderId
     * ORDER BY accessed_at DESC
     * }</pre>
     *
     * @param actorAccountHolderId the actor
     * @return possibly empty list, most recent first
     */
    List<RiskAccessAudit> findByActorAccountHolderIdOrderByAccessedAtDesc(UUID actorAccountHolderId);

    /**
     * Lists break-glass accesses awaiting their post-use review.
     *
     * <pre>{@code
     * SELECT * FROM risk_access_audit
     * WHERE break_glass = TRUE AND accessed_at >= :from
     * ORDER BY accessed_at
     * }</pre>
     *
     * <p>Every row here carries a grant reference and requires a review, because the constraint refuses
     * a break-glass record without both.</p>
     *
     * @param from inclusive start of the window
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT *
            FROM risk_access_audit
            WHERE break_glass = TRUE AND accessed_at >= :from
            ORDER BY accessed_at
            """)
    List<RiskAccessAudit> findBreakGlassSince(@Param("from") Instant from);
}
