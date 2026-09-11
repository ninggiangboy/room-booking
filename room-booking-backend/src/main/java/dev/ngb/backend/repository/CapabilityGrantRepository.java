package dev.ngb.backend.repository;

import dev.ngb.backend.model.CapabilityGrant;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evaluates what a principal is actually authorized to do, and to which resource.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code capability_grants}. Grants are revoked by recording a revocation, not by
 * deletion, so the reason a principal once held a capability survives.</p>
 */
public interface CapabilityGrantRepository extends ListCrudRepository<CapabilityGrant, UUID> {

    /**
     * Returns the grants in force for a principal over one resource at one instant.
     *
     * <p>{@code @Query} supplies the SQL because the caller must bind its own decision instant, and
     * because a resource-scoped check must also pick up the principal's global grants:</p>
     *
     * <pre>{@code
     * SELECT *
     * FROM capability_grants
     * WHERE grantee_id = :granteeId
     *   AND revoked_at IS NULL
     *   AND effective_from <= :decisionInstant
     *   AND (effective_until IS NULL OR effective_until > :decisionInstant)
     *   AND (scope_type = 'GLOBAL' OR (scope_type = :scopeType AND scope_id = :scopeId))
     * }</pre>
     *
     * <p>The span is half-open, so a grant is no longer in force at exactly {@code effective_until}.
     * This answers only what was granted: an authorization decision must subtract the active
     * restrictions from {@code capability_restrictions} before concluding anything.</p>
     *
     * @param granteeId principal being evaluated
     * @param scopeType kind of resource being acted on
     * @param scopeId identifier of that resource
     * @param decisionInstant the command's single decision instant
     * @return possibly empty list of grants in force
     */
    @Query("""
            SELECT *
            FROM capability_grants
            WHERE grantee_id = :granteeId
              AND revoked_at IS NULL
              AND effective_from <= :decisionInstant
              AND (effective_until IS NULL OR effective_until > :decisionInstant)
              AND (scope_type = 'GLOBAL' OR (scope_type = :scopeType AND scope_id = :scopeId))
            """)
    List<CapabilityGrant> findEffective(
            @Param("granteeId") UUID granteeId,
            @Param("scopeType") String scopeType,
            @Param("scopeId") UUID scopeId,
            @Param("decisionInstant") Instant decisionInstant);

    /**
     * Returns every grant delegated from one grant, so revocation can cascade.
     *
     * <p>Spring derives {@code WHERE derived_from_grant_id = ?}. Revoking a delegation without
     * walking this edge would leave a co-host holding authority the owner believes they withdrew.
     * Callers repeat the walk transitively; the grant graph is deliberately shallow.</p>
     *
     * @param derivedFromGrantId grant being revoked
     * @return possibly empty list of directly derived grants
     */
    List<CapabilityGrant> findAllByDerivedFromGrantId(UUID derivedFromGrantId);

    /**
     * Returns every grant recorded for a principal, newest first, revoked ones included.
     *
     * <p>Spring derives {@code WHERE grantee_id = ? ORDER BY effective_from DESC}. This is the
     * operator view, which needs the withdrawn grants that {@link #findEffective} deliberately
     * excludes.</p>
     *
     * @param granteeId principal whose history is being reviewed
     * @return possibly empty list of grants, newest first
     */
    List<CapabilityGrant> findAllByGranteeIdOrderByEffectiveFromDesc(UUID granteeId);
}
