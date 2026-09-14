package dev.ngb.backend.admin.internal.repository.role;

import dev.ngb.backend.admin.internal.model.role.BreakGlassGrant;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.admin.internal.model.role.BreakGlassGrant;


/**
 * Reads emergency access: what was opened, by whom, and whether anybody has looked at it since.
 *
 * <p>The review queue is the point of this table. An expired grant nobody reviewed is the audit
 * finding, and it is found here rather than remembered.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code break_glass_grants}.</p>
 */
public interface BreakGlassGrantRepository extends ListCrudRepository<BreakGlassGrant, UUID> {

    /**
     * Lists one operator emergency grants, newest first.
     *
     * @param operatorId the operator
     * @return possibly empty list, most recent first
     */
    List<BreakGlassGrant> findByOperatorIdOrderByGrantedAtDesc(UUID operatorId);

    /**
     * Lists the emergency access opened for one incident.
     *
     * @param incidentReference the incident
     * @return possibly empty list
     */
    List<BreakGlassGrant> findByIncidentReference(String incidentReference);

    /**
     * Lists the grants that are open right now, which is what an alerting view watches.
     *
     * <pre>{@code
     * SELECT * FROM break_glass_grants
     * WHERE closed_at IS NULL AND expires_at > :asOf
     * ORDER BY expires_at
     * }</pre>
     *
     * @param asOf the instant to measure against
     * @return possibly empty list, soonest to expire first
     */
    @Query("""
            SELECT * FROM break_glass_grants
            WHERE closed_at IS NULL AND expires_at > :asOf
            ORDER BY expires_at
            """)
    List<BreakGlassGrant> findOpen(@Param("asOf") Instant asOf);

    /**
     * Lists the grants whose post-use review is overdue. This is the query the control exists for:
     * bounded emergency access that nobody examined afterwards is ordinary access with paperwork.
     *
     * <pre>{@code
     * SELECT * FROM break_glass_grants
     * WHERE review_state IN ('PENDING', 'IN_REVIEW') AND review_due_at <= :asOf
     * ORDER BY review_due_at
     * }</pre>
     *
     * @param asOf the instant to measure against
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT * FROM break_glass_grants
            WHERE review_state IN ('PENDING', 'IN_REVIEW') AND review_due_at <= :asOf
            ORDER BY review_due_at
            """)
    List<BreakGlassGrant> findReviewOverdue(@Param("asOf") Instant asOf);

    /**
     * Lists the grants whose review found something, which is what a governance report reads.
     *
     * <pre>{@code
     * SELECT * FROM break_glass_grants
     * WHERE review_finding IS NOT NULL AND review_finding <> 'APPROPRIATE'
     * ORDER BY reviewed_at DESC
     * }</pre>
     *
     * @return possibly empty list, most recently reviewed first
     */
    @Query("""
            SELECT * FROM break_glass_grants
            WHERE review_finding IS NOT NULL AND review_finding <> 'APPROPRIATE'
            ORDER BY reviewed_at DESC
            """)
    List<BreakGlassGrant> findAdverseFindings();

    /**
     * Lists the grants that expired without ever being used, which is worth knowing: access asked
     * for and not needed is a signal about how the ordinary path is failing.
     *
     * <pre>{@code
     * SELECT * FROM break_glass_grants
     * WHERE activity_count = 0 AND closed_at IS NOT NULL
     * ORDER BY granted_at DESC
     * }</pre>
     *
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM break_glass_grants
            WHERE activity_count = 0 AND closed_at IS NOT NULL
            ORDER BY granted_at DESC
            """)
    List<BreakGlassGrant> findUnused();
}
