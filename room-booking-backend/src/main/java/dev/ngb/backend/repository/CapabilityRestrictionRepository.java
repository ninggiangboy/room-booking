package dev.ngb.backend.repository;

import dev.ngb.backend.model.CapabilityRestriction;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads the withdrawals that are subtracted from a principal's grants.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code capability_restrictions}. A restriction is lifted by recording who
 * lifted it, never by editing its history.</p>
 */
public interface CapabilityRestrictionRepository
        extends ListCrudRepository<CapabilityRestriction, UUID> {

    /**
     * Returns the restrictions suppressing authority for a principal over a resource at an instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM capability_restrictions
     * WHERE principal_id = :principalId
     *   AND lifted_at IS NULL
     *   AND effective_from <= :decisionInstant
     *   AND (effective_until IS NULL OR effective_until > :decisionInstant)
     *   AND (scope_type = 'GLOBAL' OR (scope_type = :scopeType AND scope_id = :scopeId))
     * }</pre>
     *
     * <p>Mirrors {@code CapabilityGrantRepository.findEffective} exactly, including picking up
     * global restrictions during a resource-scoped check: a platform-wide suspension has to bite on
     * every resource, not only on those named individually.</p>
     *
     * @param principalId principal being evaluated
     * @param scopeType kind of resource being acted on
     * @param scopeId identifier of that resource
     * @param decisionInstant the command's single decision instant
     * @return possibly empty list of restrictions in force
     */
    @Query("""
            SELECT *
            FROM capability_restrictions
            WHERE principal_id = :principalId
              AND lifted_at IS NULL
              AND effective_from <= :decisionInstant
              AND (effective_until IS NULL OR effective_until > :decisionInstant)
              AND (scope_type = 'GLOBAL' OR (scope_type = :scopeType AND scope_id = :scopeId))
            """)
    List<CapabilityRestriction> findActive(
            @Param("principalId") UUID principalId,
            @Param("scopeType") String scopeType,
            @Param("scopeId") UUID scopeId,
            @Param("decisionInstant") Instant decisionInstant);

    /**
     * Returns every restriction recorded for a principal, newest first.
     *
     * <p>Spring derives {@code WHERE principal_id = ? ORDER BY effective_from DESC}. Includes lifted
     * restrictions, which an appeal review needs to see.</p>
     *
     * @param principalId principal whose history is being reviewed
     * @return possibly empty list of restrictions, newest first
     */
    List<CapabilityRestriction> findAllByPrincipalIdOrderByEffectiveFromDesc(UUID principalId);
}
