package dev.ngb.backend.repository;

import dev.ngb.backend.model.AuthSession;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and revokes durable sign-ins.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code auth_sessions}. Sessions are revoked by recording a revocation rather
 * than deleted, so "this session was signed out from that device at that time" stays answerable.</p>
 */
public interface AuthSessionRepository extends ListCrudRepository<AuthSession, UUID> {

    /**
     * Returns the sessions a user could still act through, most recently used first.
     *
     * <pre>{@code
     * SELECT *
     * FROM auth_sessions
     * WHERE user_id = :userId
     *   AND revoked_at IS NULL
     *   AND idle_expires_at > :decisionInstant
     *   AND absolute_expires_at > :decisionInstant
     * ORDER BY last_used_at DESC
     * }</pre>
     *
     * <p>Both expiries are checked because they mean different things: one ends a session that has
     * gone quiet, the other ends one that has lived too long however actively it is used. This backs
     * the "your devices" list, so it must show exactly what an attacker could still use.</p>
     *
     * @param userId owner whose sessions are listed
     * @param decisionInstant the command's single decision instant
     * @return possibly empty list of live sessions
     */
    @Query("""
            SELECT *
            FROM auth_sessions
            WHERE user_id = :userId
              AND revoked_at IS NULL
              AND idle_expires_at > :decisionInstant
              AND absolute_expires_at > :decisionInstant
            ORDER BY last_used_at DESC
            """)
    List<AuthSession> findLiveForUser(
            @Param("userId") UUID userId,
            @Param("decisionInstant") Instant decisionInstant);

    /**
     * Loads a session and locks it for the duration of the transaction.
     *
     * <pre>{@code
     * SELECT * FROM auth_sessions WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction.</strong> Refresh rotation reads the current
     * generation and writes the next one; without the lock, two concurrent refreshes could both
     * succeed from the same generation, which is indistinguishable from the token theft the
     * rotation chain exists to detect.</p>
     *
     * @param id session to lock
     * @return the locked session when it exists
     */
    @Query("SELECT * FROM auth_sessions WHERE id = :id FOR UPDATE")
    Optional<AuthSession> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Returns unrevoked sessions that have passed their absolute expiry, for the sweep.
     *
     * <pre>{@code
     * SELECT *
     * FROM auth_sessions
     * WHERE revoked_at IS NULL
     *   AND absolute_expires_at <= :decisionInstant
     * ORDER BY absolute_expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>The instant is bound by the caller rather than read by the database, and the batch is
     * bounded so one sweep cannot claim an unbounded backlog.</p>
     *
     * @param decisionInstant the sweep's single decision instant
     * @param batchSize maximum number of sessions to return
     * @return possibly empty list of expired sessions, oldest first
     */
    @Query("""
            SELECT *
            FROM auth_sessions
            WHERE revoked_at IS NULL
              AND absolute_expires_at <= :decisionInstant
            ORDER BY absolute_expires_at
            LIMIT :batchSize
            """)
    List<AuthSession> findExpired(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);
}
