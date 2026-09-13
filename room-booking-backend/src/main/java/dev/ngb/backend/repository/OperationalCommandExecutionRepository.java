package dev.ngb.backend.repository;

import dev.ngb.backend.model.OperationalCommandExecution;
import dev.ngb.backend.model.CommandDomainOutcome;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what operators asked for and what the domains answered.
 *
 * <p>Refusals are first-class here. An administrative log that only records successes is one that
 * makes every denied attempt look like it never happened, so the queries below deliberately do not
 * filter them out.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code operational_command_executions}.</p>
 */
public interface OperationalCommandExecutionRepository extends ListCrudRepository<OperationalCommandExecution, UUID> {

    /**
     * Finds an execution by the key its caller supplied, which is what makes a retry the same
     * execution rather than a second one.
     *
     * @param commandKey the command
     * @param idempotencyKey the caller key
     * @return the execution, when it was already recorded
     */
    Optional<OperationalCommandExecution> findByCommandKeyAndIdempotencyKey(String commandKey,
            String idempotencyKey);

    /**
     * Lists what one operator has issued, newest first.
     *
     * @param actorId the operator
     * @return possibly empty list, most recent first
     */
    List<OperationalCommandExecution> findByActorIdOrderByRequestedAtDesc(UUID actorId);

    /**
     * Lists everything an operator has done to one target, which is the question asked when
     * somebody notices a booking or a payment in a state nobody expected.
     *
     * <pre>{@code
     * SELECT * FROM operational_command_executions
     * WHERE target_type = :targetType AND target_id = :targetId
     * ORDER BY requested_at DESC
     * }</pre>
     *
     * @param targetType kind of thing
     * @param targetId the row
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM operational_command_executions
            WHERE target_type = :targetType AND target_id = :targetId
            ORDER BY requested_at DESC
            """)
    List<OperationalCommandExecution> findForTarget(@Param("targetType") String targetType,
            @Param("targetId") UUID targetId);

    /**
     * Lists the executions waiting for their approvals, which is the approver queue.
     *
     * <pre>{@code
     * SELECT * FROM operational_command_executions
     * WHERE dispatch_state = 'PENDING_APPROVAL'
     * ORDER BY requested_at
     * }</pre>
     *
     * @return possibly empty list, longest waiting first
     */
    @Query("""
            SELECT * FROM operational_command_executions
            WHERE dispatch_state = 'PENDING_APPROVAL'
            ORDER BY requested_at
            """)
    List<OperationalCommandExecution> findAwaitingApproval();

    /**
     * Counts what one operator has issued of one command today, which is what the daily limit on
     * the command is measured against.
     *
     * <pre>{@code
     * SELECT count(*) FROM operational_command_executions
     * WHERE actor_id = :actorId AND command_key = :commandKey
     *   AND requested_at >= :from AND requested_at < :to
     * }</pre>
     *
     * @param actorId the operator
     * @param commandKey the command
     * @param from start of the window, inclusive
     * @param to end of the window, exclusive
     * @return how many were issued in the window
     */
    @Query("""
            SELECT count(*) FROM operational_command_executions
            WHERE actor_id = :actorId AND command_key = :commandKey
              AND requested_at >= :from AND requested_at < :to
            """)
    long countIssuedBetween(@Param("actorId") UUID actorId,
            @Param("commandKey") String commandKey, @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Sums the money one operator moved in a window, across every command that moves any. This is
     * the number a financial-control review asks for and the one no single ceiling answers.
     *
     * <pre>{@code
     * SELECT coalesce(sum(amount_minor), 0) FROM operational_command_executions
     * WHERE actor_id = :actorId AND amount_currency = :currency
     *   AND dispatch_state = 'SUCCEEDED' AND domain_outcome = 'APPLIED'
     *   AND completed_at >= :from AND completed_at < :to
     * }</pre>
     *
     * @param actorId the operator
     * @param currency ISO 4217 alphabetic code
     * @param from start of the window, inclusive
     * @param to end of the window, exclusive
     * @return total moved in integer minor units, zero when nothing was
     */
    @Query("""
            SELECT coalesce(sum(amount_minor), 0) FROM operational_command_executions
            WHERE actor_id = :actorId AND amount_currency = :currency
              AND dispatch_state = 'SUCCEEDED' AND domain_outcome = 'APPLIED'
              AND completed_at >= :from AND completed_at < :to
            """)
    long sumAppliedAmount(@Param("actorId") UUID actorId, @Param("currency") String currency,
            @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Lists the executions the owning domain refused, which is where a console that is offering
     * operators actions their authority does not actually permit becomes visible.
     *
     * <pre>{@code
     * SELECT * FROM operational_command_executions
     * WHERE dispatch_state = 'REFUSED' AND completed_at >= :from AND completed_at < :to
     * ORDER BY completed_at
     * }</pre>
     *
     * @param from start of the window, inclusive
     * @param to end of the window, exclusive
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM operational_command_executions
            WHERE dispatch_state = 'REFUSED' AND completed_at >= :from AND completed_at < :to
            ORDER BY completed_at
            """)
    List<OperationalCommandExecution> findRefusedBetween(@Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Lists what was issued under emergency authority, which is what the review of a break-glass
     * grant reads alongside the activity log.
     *
     * <pre>{@code
     * SELECT * FROM operational_command_executions
     * WHERE break_glass_grant_id = :breakGlassGrantId
     * ORDER BY requested_at
     * }</pre>
     *
     * @param breakGlassGrantId the emergency grant
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM operational_command_executions
            WHERE break_glass_grant_id = :breakGlassGrantId
            ORDER BY requested_at
            """)
    List<OperationalCommandExecution> findUnderEmergencyAccess(
            @Param("breakGlassGrantId") UUID breakGlassGrantId);

    /**
     * Counts what each domain answered in a window, so the shape of operator intervention is
     * readable without pulling every row.
     *
     * <pre>{@code
     * SELECT domain_outcome AS outcome, count(*) AS execution_count
     * FROM operational_command_executions
     * WHERE completed_at >= :from AND completed_at < :to AND domain_outcome IS NOT NULL
     * GROUP BY domain_outcome
     * ORDER BY execution_count DESC
     * }</pre>
     *
     * @param from start of the window, inclusive
     * @param to end of the window, exclusive
     * @return possibly empty list, busiest outcome first
     */
    @Query("""
            SELECT domain_outcome AS outcome, count(*) AS execution_count
            FROM operational_command_executions
            WHERE completed_at >= :from AND completed_at < :to AND domain_outcome IS NOT NULL
            GROUP BY domain_outcome
            ORDER BY execution_count DESC
            """)
    List<OutcomeCount> countByOutcomeBetween(@Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * How many executions ended in one outcome.
     *
     * <p>Projection for {@link #countByOutcomeBetween}.</p>
     *
     * @param outcome what the owning domain did
     * @param executionCount how many executions ended that way
     */
    record OutcomeCount(CommandDomainOutcome outcome, long executionCount) {
    }
}
