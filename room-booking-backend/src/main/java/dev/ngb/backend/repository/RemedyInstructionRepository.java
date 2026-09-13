package dev.ngb.backend.repository;

import dev.ngb.backend.model.RemedyInstruction;
import dev.ngb.backend.model.InstructionTargetDomain;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the instructions sent to the domains that own the effects.
 *
 * <p>A lost response is queried under the same idempotency key; nothing here creates a second
 * identity for work that may already have happened.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code remedy_instructions}.</p>
 */
public interface RemedyInstructionRepository extends ListCrudRepository<RemedyInstruction, UUID> {

    /**
     * Finds the instruction a command identity already created.
     *
     * @param commandId command identity
     * @return the instruction, when that command already ran
     */
    Optional<RemedyInstruction> findByCommandId(String commandId);

    /**
     * Finds the instruction an idempotency key already sent to a domain.
     *
     * @param targetDomain receiving domain
     * @param idempotencyKey key that domain deduplicates on
     * @return the instruction, when that key already went out
     */
    Optional<RemedyInstruction> findByTargetDomainAndIdempotencyKey(InstructionTargetDomain targetDomain,
            String idempotencyKey);

    /**
     * Lists the instructions raised for one remedy.
     *
     * @param caseRemedyId remedy
     * @return possibly empty list
     */
    List<RemedyInstruction> findByCaseRemedyId(UUID caseRemedyId);

    /**
     * Claims instructions whose outcome is still open and due to be queried.
     *
     * <pre>{@code
     * SELECT *
     * FROM remedy_instructions
     * WHERE state IN ('DISPATCHED', 'PENDING', 'UNKNOWN')
     *   AND next_query_at IS NOT NULL
     *   AND next_query_at <= :at
     * ORDER BY next_query_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. Querying is how a retry happens; the identity never changes.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum instructions to claim
     * @return possibly empty list, longest due first
     */
    @Query("""
            SELECT *
            FROM remedy_instructions
            WHERE state IN ('DISPATCHED', 'PENDING', 'UNKNOWN')
              AND next_query_at IS NOT NULL
              AND next_query_at <= :at
            ORDER BY next_query_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<RemedyInstruction> claimDueForQuery(@Param("at") Instant at, @Param("batchSize") int batchSize);

    /**
     * Lists the instructions still in flight on a case.
     *
     * <pre>{@code
     * SELECT * FROM remedy_instructions
     * WHERE support_case_id = :supportCaseId
     *   AND state IN ('PREPARED', 'DISPATCHED', 'ACCEPTED', 'PENDING', 'UNKNOWN')
     * }</pre>
     *
     * <p>The same predicate the closure guard evaluates, so a caller can show why a case will not close
     * rather than offer a button that fails.</p>
     *
     * @param supportCaseId case
     * @return possibly empty list of instructions not yet finished
     */
    @Query("""
            SELECT * FROM remedy_instructions
            WHERE support_case_id = :supportCaseId
              AND state IN ('PREPARED', 'DISPATCHED', 'ACCEPTED', 'PENDING', 'UNKNOWN')
            """)
    List<RemedyInstruction> findInFlight(@Param("supportCaseId") UUID supportCaseId);
}
