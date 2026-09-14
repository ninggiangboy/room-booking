package dev.ngb.backend.support.internal.repository.case_;

import dev.ngb.backend.support.internal.model.case_.CaseSlaClock;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.support.internal.model.case_.CaseSlaClock;


/**
 * Reads the independent deadline clocks on a case.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_sla_clocks}.</p>
 */
public interface CaseSlaClockRepository extends ListCrudRepository<CaseSlaClock, UUID> {

    /**
     * Finds the effective clock of one type on a case episode.
     *
     * <pre>{@code
     * SELECT * FROM case_sla_clocks
     * WHERE support_case_id = :supportCaseId
     *   AND lifecycle_episode = :lifecycleEpisode
     *   AND clock_type = :clockType
     *   AND state IN ('RUNNING', 'PAUSED')
     *   AND NOT parallel_deadline_permitted
     * }</pre>
     *
     * <p>Matches {@code uk_case_sla_clocks_effective}, so at most one row can come back unless the
     * policy explicitly supports parallel deadlines.</p>
     *
     * @param supportCaseId case
     * @param lifecycleEpisode episode
     * @param clockType clock type
     * @return the effective clock, when one is running
     */
    @Query("""
            SELECT * FROM case_sla_clocks
            WHERE support_case_id = :supportCaseId
              AND lifecycle_episode = :lifecycleEpisode
              AND clock_type = :clockType
              AND state IN ('RUNNING', 'PAUSED')
              AND NOT parallel_deadline_permitted
            """)
    Optional<CaseSlaClock> findEffective(@Param("supportCaseId") UUID supportCaseId,
            @Param("lifecycleEpisode") short lifecycleEpisode,
            @Param("clockType") String clockType);

    /**
     * Claims clocks that have passed their due instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM case_sla_clocks
     * WHERE state IN ('RUNNING', 'PAUSED') AND due_at <= :at
     * ORDER BY due_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. A paused clock is included because a pause never suspends a
     * statutory or provider deadline, only the ones policy allowlists.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum clocks to claim
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT *
            FROM case_sla_clocks
            WHERE state IN ('RUNNING', 'PAUSED') AND due_at <= :at
            ORDER BY due_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<CaseSlaClock> claimBreached(@Param("at") Instant at, @Param("batchSize") int batchSize);
}
