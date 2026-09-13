package dev.ngb.backend.repository;

import dev.ngb.backend.model.CaseRemedy;
import dev.ngb.backend.model.CaseRemedyState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and locks the remedies a decision authorized.
 *
 * <p>Authorization and execution are different columns on purpose, so nothing here reads a remedy to
 * decide whether money moved: the instruction rows say that.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_remedies}.</p>
 */
public interface CaseRemedyRepository extends ListCrudRepository<CaseRemedy, UUID> {

    /**
     * Lists the remedies of a case in one state.
     *
     * @param supportCaseId case
     * @param state remedy state
     * @return possibly empty list
     */
    List<CaseRemedy> findBySupportCaseIdAndState(UUID supportCaseId, CaseRemedyState state);

    /**
     * Lists the remedies a decision authorized.
     *
     * @param caseDecisionId decision
     * @return possibly empty list
     */
    List<CaseRemedy> findByCaseDecisionId(UUID caseDecisionId);

    /**
     * Locks one remedy before it is approved, executed or reversed.
     *
     * <pre>{@code
     * SELECT * FROM case_remedies WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Requires an active transaction. The remedy total and its lines must agree at commit, so both
     * are written under this lock.</p>
     *
     * @param id remedy to lock
     * @return the locked remedy, when it exists
     */
    @Query("SELECT * FROM case_remedies WHERE id = :id FOR UPDATE")
    Optional<CaseRemedy> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Claims remedies whose outcome is unknown and due to be reconciled.
     *
     * <pre>{@code
     * SELECT *
     * FROM case_remedies
     * WHERE state IN ('UNKNOWN', 'RECONCILING')
     *   AND next_reconciliation_at IS NOT NULL
     *   AND next_reconciliation_at <= :at
     * ORDER BY next_reconciliation_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. Reconciliation never creates a new remedy merely because the
     * original response was lost.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum remedies to claim
     * @return possibly empty list, longest due first
     */
    @Query("""
            SELECT *
            FROM case_remedies
            WHERE state IN ('UNKNOWN', 'RECONCILING')
              AND next_reconciliation_at IS NOT NULL
              AND next_reconciliation_at <= :at
            ORDER BY next_reconciliation_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<CaseRemedy> claimDueForReconciliation(@Param("at") Instant at,
            @Param("batchSize") int batchSize);

    /**
     * Claims authorized remedies that lapsed unused.
     *
     * <pre>{@code
     * SELECT *
     * FROM case_remedies
     * WHERE state IN ('PROPOSED', 'APPROVAL_PENDING', 'APPROVED')
     *   AND expires_at IS NOT NULL
     *   AND expires_at <= :at
     * ORDER BY expires_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum remedies to claim
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT *
            FROM case_remedies
            WHERE state IN ('PROPOSED', 'APPROVAL_PENDING', 'APPROVED')
              AND expires_at IS NOT NULL
              AND expires_at <= :at
            ORDER BY expires_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<CaseRemedy> claimExpired(@Param("at") Instant at, @Param("batchSize") int batchSize);
}
