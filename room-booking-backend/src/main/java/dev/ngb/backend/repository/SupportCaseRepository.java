package dev.ngb.backend.repository;

import dev.ngb.backend.model.SupportCase;
import dev.ngb.backend.model.SupportCaseState;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and locks support cases.
 *
 * <p>The claim and lock methods exist because a transition carries an SLA effect and an outbound
 * event, so two workers acting on one case at once is exactly what must not happen.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code support_cases}.</p>
 */
public interface SupportCaseRepository extends ListCrudRepository<SupportCase, UUID> {

    /**
     * Finds a case by the reference a participant would quote.
     *
     * @param caseReference human-quotable reference
     * @return the case, when it exists
     */
    Optional<SupportCase> findByCaseReference(String caseReference);

    /**
     * Finds the case an intake command already opened.
     *
     * <pre>{@code
     * SELECT * FROM support_cases
     * WHERE source_channel = :sourceChannel
     *   AND reporter_account_holder_id IS NOT DISTINCT FROM :reporterAccountHolderId
     *   AND intake_command_id = :intakeCommandId
     * }</pre>
     *
     * <p>Matches {@code uk_support_cases_intake}, so a retrying client converges on the one case rather
     * than opening a second investigation of the same problem.</p>
     *
     * @param sourceChannel channel the report arrived on
     * @param reporterAccountHolderId reporter, or null for an anonymous report
     * @param intakeCommandId client command identity
     * @return the case that command opened, when it did
     */
    @Query("""
            SELECT * FROM support_cases
            WHERE source_channel = :sourceChannel
              AND reporter_account_holder_id IS NOT DISTINCT FROM :reporterAccountHolderId
              AND intake_command_id = :intakeCommandId
            """)
    Optional<SupportCase> findByIntakeCommand(@Param("sourceChannel") String sourceChannel,
            @Param("reporterAccountHolderId") @Nullable UUID reporterAccountHolderId,
            @Param("intakeCommandId") String intakeCommandId);

    /**
     * Locks one case for a transition.
     *
     * <pre>{@code
     * SELECT * FROM support_cases WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Requires an active transaction. Every state change takes this lock, because the case version
     * an actor expected to be acting on is checked against the row it is about to change.</p>
     *
     * @param id case to lock
     * @return the locked case, when it exists
     */
    @Query("SELECT * FROM support_cases WHERE id = :id FOR UPDATE")
    Optional<SupportCase> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Claims open cases whose provider deadline is approaching.
     *
     * <pre>{@code
     * SELECT *
     * FROM support_cases
     * WHERE state <> 'CLOSED'
     *   AND provider_deadline_at IS NOT NULL
     *   AND provider_deadline_at <= :at
     * ORDER BY provider_deadline_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. A statutory or provider deadline does not pause, so a job
     * running late still finds the cases it should already have escalated.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum cases to claim
     * @return possibly empty list, nearest deadline first
     */
    @Query("""
            SELECT *
            FROM support_cases
            WHERE state <> 'CLOSED'
              AND provider_deadline_at IS NOT NULL
              AND provider_deadline_at <= :at
            ORDER BY provider_deadline_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<SupportCase> claimApproachingProviderDeadlines(@Param("at") Instant at,
            @Param("batchSize") int batchSize);

    /**
     * Lists the open cases assigned to one agent, for their workspace.
     *
     * @param ownerAccountHolderId assigned agent
     * @param state case state
     * @return possibly empty list
     */
    List<SupportCase> findByOwnerAccountHolderIdAndState(UUID ownerAccountHolderId,
            SupportCaseState state);
}
