package dev.ngb.backend.support.internal.repository.claim;

import dev.ngb.backend.support.internal.model.claim.DamageClaim;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.support.internal.model.claim.DamageClaim;


/**
 * Reads and locks damage claims.
 *
 * <p>Claim state is its own dimension: a claim can be decided while payment is pending and an appeal
 * is open, so nothing here reads a claim to infer what payment or finance is doing.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code damage_claims}.</p>
 */
public interface DamageClaimRepository extends ListCrudRepository<DamageClaim, UUID> {

    /**
     * Finds a claim by the reference a participant would quote.
     *
     * @param claimReference human-quotable reference
     * @return the claim, when it exists
     */
    Optional<DamageClaim> findByClaimReference(String claimReference);

    /**
     * Lists the claims raised on a case.
     *
     * @param supportCaseId case
     * @return possibly empty list
     */
    List<DamageClaim> findBySupportCaseId(UUID supportCaseId);

    /**
     * Locks one claim before its decision or its totals change.
     *
     * <pre>{@code
     * SELECT * FROM damage_claims WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Requires an active transaction. The accepted total and the item totals must agree at commit,
     * so both are written under this lock.</p>
     *
     * @param id claim to lock
     * @return the locked claim, when it exists
     */
    @Query("SELECT * FROM damage_claims WHERE id = :id FOR UPDATE")
    Optional<DamageClaim> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Claims claims whose respondent window has closed.
     *
     * <pre>{@code
     * SELECT *
     * FROM damage_claims
     * WHERE state NOT IN ('CLOSED', 'WITHDRAWN', 'SETTLED')
     *   AND respondent_deadline_at IS NOT NULL
     *   AND respondent_deadline_at <= :at
     * ORDER BY respondent_deadline_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. A respondent who did not answer does not lose by default; the
     * window closing only moves the claim on to adjudication.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum claims to claim
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT *
            FROM damage_claims
            WHERE state NOT IN ('CLOSED', 'WITHDRAWN', 'SETTLED')
              AND respondent_deadline_at IS NOT NULL
              AND respondent_deadline_at <= :at
            ORDER BY respondent_deadline_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<DamageClaim> claimRespondentWindowClosed(@Param("at") Instant at,
            @Param("batchSize") int batchSize);
}
