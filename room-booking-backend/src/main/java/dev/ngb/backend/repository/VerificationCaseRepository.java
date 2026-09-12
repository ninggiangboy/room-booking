package dev.ngb.backend.repository;

import dev.ngb.backend.model.VerificationCase;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the verification questions asked about a host and the evidence gathered for them.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code verification_cases}. Nothing here confers a capability: cases are
 * evidence, and {@code host_eligibility_decisions} records what the platform concluded.</p>
 */
public interface VerificationCaseRepository extends ListCrudRepository<VerificationCase, UUID> {

    /**
     * Finds the live case of one kind for a profile, if any.
     *
     * <pre>{@code
     * SELECT *
     * FROM verification_cases
     * WHERE host_legal_profile_id = :profileId
     *   AND case_type = :caseType
     *   AND status NOT IN ('COMPLETED', 'ABANDONED')
     * }</pre>
     *
     * <p>{@code uk_verification_cases_one_open} guarantees at most one row matches. This is the check
     * that stops a second identity case being opened alongside a running one, where the loser's
     * evidence would silently disappear.</p>
     *
     * @param profileId legal profile being verified
     * @param caseType question being asked
     * @return the live case when one is open
     */
    @Query("""
            SELECT *
            FROM verification_cases
            WHERE host_legal_profile_id = :profileId
              AND case_type = :caseType
              AND status NOT IN ('COMPLETED', 'ABANDONED')
            """)
    Optional<VerificationCase> findOpenCase(
            @Param("profileId") UUID profileId,
            @Param("caseType") String caseType);

    /**
     * Returns the passed cases for a profile that have not gone stale.
     *
     * <pre>{@code
     * SELECT *
     * FROM verification_cases
     * WHERE host_legal_profile_id = :profileId
     *   AND status = 'COMPLETED'
     *   AND outcome = 'PASSED'
     *   AND (expires_at IS NULL OR expires_at > :decisionInstant)
     * }</pre>
     *
     * <p>Expiry is checked here rather than left to the caller because a passed case that has expired
     * is not evidence any more, and an eligibility decision built on one would be conferring
     * authority from a check nobody has repeated. The instant is bound by the caller.</p>
     *
     * @param profileId legal profile being assessed
     * @param decisionInstant the decision's single instant
     * @return possibly empty list of currently valid evidence
     */
    @Query("""
            SELECT *
            FROM verification_cases
            WHERE host_legal_profile_id = :profileId
              AND status = 'COMPLETED'
              AND outcome = 'PASSED'
              AND (expires_at IS NULL OR expires_at > :decisionInstant)
            """)
    List<VerificationCase> findCurrentEvidence(
            @Param("profileId") UUID profileId,
            @Param("decisionInstant") Instant decisionInstant);

    /**
     * Returns cases whose verification has gone stale, for the re-screening sweep.
     *
     * <pre>{@code
     * SELECT *
     * FROM verification_cases
     * WHERE status = 'COMPLETED'
     *   AND expires_at IS NOT NULL
     *   AND expires_at <= :decisionInstant
     * ORDER BY expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>The batch is bounded and the instant is bound by the caller, so the sweep cannot claim an
     * unbounded backlog and the index needs no moving time predicate.</p>
     *
     * @param decisionInstant the sweep's single decision instant
     * @param batchSize maximum number of cases to return
     * @return possibly empty list of stale cases, oldest expiry first
     */
    @Query("""
            SELECT *
            FROM verification_cases
            WHERE status = 'COMPLETED'
              AND expires_at IS NOT NULL
              AND expires_at <= :decisionInstant
            ORDER BY expires_at
            LIMIT :batchSize
            """)
    List<VerificationCase> findStale(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);
}
