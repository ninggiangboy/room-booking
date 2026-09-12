package dev.ngb.backend.repository;

import dev.ngb.backend.model.AccessGrant;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads platform access entitlements.
 *
 * <p>The unresolved queue matters as much as the live one: an entitlement whose provider outcome is
 * not known must be treated as possibly open, not quietly ignored.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code access_grants}.</p>
 */
public interface AccessGrantRepository extends ListCrudRepository<AccessGrant, UUID> {

    /**
     * Lists the entitlements still capable of opening a door for a stay.
     *
     * <pre>{@code
     * SELECT *
     * FROM access_grants
     * WHERE operational_stay_id = :operationalStayId
     *   AND state IN ('ELIGIBLE', 'PROVISIONING', 'ACTIVE', 'UNKNOWN', 'REVOKING')
     * ORDER BY valid_from
     * }</pre>
     *
     * <p>The state list matches {@code uk_access_grants_live}, which is what stops two live credentials
     * existing for one person, unit and mode.</p>
     *
     * @param operationalStayId stay
     * @return possibly empty list, earliest window first
     */
    @Query("""
            SELECT *
            FROM access_grants
            WHERE operational_stay_id = :operationalStayId
              AND state IN ('ELIGIBLE', 'PROVISIONING', 'ACTIVE', 'UNKNOWN', 'REVOKING')
            ORDER BY valid_from
            """)
    List<AccessGrant> findLive(@Param("operationalStayId") UUID operationalStayId);

    /**
     * Lists entitlements whose fulfilment outcome is unresolved.
     *
     * <pre>{@code
     * SELECT *
     * FROM access_grants
     * WHERE state IN ('PROVISIONING', 'UNKNOWN', 'REVOKING')
     *   AND updated_at <= :stillUnresolvedAt
     * ORDER BY updated_at
     * }</pre>
     *
     * <p>The operator queue. A grant sitting here past a revocation request is the compromised-access
     * case, because the code may still work.</p>
     *
     * @param stillUnresolvedAt instant before which a grant counts as stuck
     * @return possibly empty list, longest stuck first
     */
    @Query("""
            SELECT *
            FROM access_grants
            WHERE state IN ('PROVISIONING', 'UNKNOWN', 'REVOKING')
              AND updated_at <= :stillUnresolvedAt
            ORDER BY updated_at
            """)
    List<AccessGrant> findUnresolved(@Param("stillUnresolvedAt") Instant stillUnresolvedAt);

    /**
     * Lists the entitlements a person holds for a stay.
     *
     * <p>Spring derives {@code WHERE operational_stay_id = ? AND subject_account_holder_id = ?}.</p>
     *
     * @param operationalStayId stay
     * @param subjectAccountHolderId person entitled to enter
     * @return possibly empty list
     */
    List<AccessGrant> findByOperationalStayIdAndSubjectAccountHolderId(UUID operationalStayId,
            UUID subjectAccountHolderId);

    /**
     * Locks one grant for a state change.
     *
     * <pre>{@code
     * SELECT * FROM access_grants WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Requires an active transaction. Revocation and rotation both take this lock before writing an
     * operation, so that two workers cannot start contradictory provider calls.</p>
     *
     * @param id grant to lock
     * @return the locked grant, when it exists
     */
    @Query("SELECT * FROM access_grants WHERE id = :id FOR UPDATE")
    Optional<AccessGrant> findByIdForUpdate(@Param("id") UUID id);
}
