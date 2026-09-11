package dev.ngb.backend.repository;

import dev.ngb.backend.model.AuthAttempt;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Records authentication attempts and counts them for velocity control.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select and insert SQL
 * for {@code auth_attempts}. Rows hold no credential material and no raw identifier.</p>
 */
public interface AuthAttemptRepository extends ListCrudRepository<AuthAttempt, UUID> {

    /**
     * Counts recent failed attempts against one account.
     *
     * <pre>{@code
     * SELECT count(*)
     * FROM auth_attempts
     * WHERE user_id = :userId
     *   AND attempt_type = :attemptType
     *   AND outcome_class = 'FAILURE'
     *   AND occurred_at >= :windowStart
     * }</pre>
     *
     * <p>Only {@code FAILURE} is counted. A challenged attempt may still succeed, and counting it
     * would let ordinary step-up traffic trip the limit meant to catch attackers. The window start
     * is derived by the caller from its own decision instant.</p>
     *
     * @param userId account being protected
     * @param attemptType kind of attempt being limited
     * @param windowStart inclusive beginning of the rate-limit window
     * @return number of failures in the window
     */
    @Query("""
            SELECT count(*)
            FROM auth_attempts
            WHERE user_id = :userId
              AND attempt_type = :attemptType
              AND outcome_class = 'FAILURE'
              AND occurred_at >= :windowStart
            """)
    long countRecentFailuresForUser(
            @Param("userId") UUID userId,
            @Param("attemptType") String attemptType,
            @Param("windowStart") Instant windowStart);

    /**
     * Counts recent failed attempts against an identifier that resolved to no account.
     *
     * <pre>{@code
     * SELECT count(*)
     * FROM auth_attempts
     * WHERE identifier_digest = :identifierDigest
     *   AND attempt_type = :attemptType
     *   AND outcome_class = 'FAILURE'
     *   AND occurred_at >= :windowStart
     * }</pre>
     *
     * <p>Needed alongside the per-account count because enumeration attacks target addresses that do
     * not exist, which would otherwise be unlimited. The digest is compared, never the address.</p>
     *
     * @param identifierDigest SHA-256 digest of the identifier tried
     * @param attemptType kind of attempt being limited
     * @param windowStart inclusive beginning of the rate-limit window
     * @return number of failures in the window
     */
    @Query("""
            SELECT count(*)
            FROM auth_attempts
            WHERE identifier_digest = :identifierDigest
              AND attempt_type = :attemptType
              AND outcome_class = 'FAILURE'
              AND occurred_at >= :windowStart
            """)
    long countRecentFailuresForIdentifier(
            @Param("identifierDigest") String identifierDigest,
            @Param("attemptType") String attemptType,
            @Param("windowStart") Instant windowStart);

    /**
     * Returns one account's recent attempts, most recent first.
     *
     * <p>Spring derives {@code WHERE user_id = ? ORDER BY occurred_at DESC}. Backs account-takeover
     * investigation, where the pattern of failures matters more than any single row.</p>
     *
     * @param userId account being investigated
     * @return possibly empty list of attempts, most recent first
     */
    List<AuthAttempt> findAllByUserIdOrderByOccurredAtDesc(UUID userId);
}
