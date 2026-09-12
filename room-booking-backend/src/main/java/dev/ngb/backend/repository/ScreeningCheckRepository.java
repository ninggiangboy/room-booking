package dev.ngb.backend.repository;

import dev.ngb.backend.model.ScreeningCheck;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads sanctions, PEP, watch-list, age, and market screening results.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code screening_checks}.</p>
 */
public interface ScreeningCheckRepository extends ListCrudRepository<ScreeningCheck, UUID> {

    /**
     * Returns screening results for a profile that still block eligibility.
     *
     * <pre>{@code
     * SELECT *
     * FROM screening_checks
     * WHERE host_legal_profile_id = :profileId
     *   AND (
     *     result = 'ERROR'
     *     OR (result IN ('POTENTIAL_MATCH', 'CONFIRMED_MATCH')
     *         AND (adjudication_outcome IS NULL
     *              OR adjudication_outcome IN ('TRUE_MATCH', 'INCONCLUSIVE')))
     *   )
     * }</pre>
     *
     * <p>Errored runs are included deliberately: a screening that could not complete must never be
     * read as one that came back clear. Unadjudicated matches block because a match is a question
     * until a named person answers it.</p>
     *
     * @param profileId legal profile being assessed
     * @return possibly empty list of blocking results
     */
    @Query("""
            SELECT *
            FROM screening_checks
            WHERE host_legal_profile_id = :profileId
              AND (
                result = 'ERROR'
                OR (result IN ('POTENTIAL_MATCH', 'CONFIRMED_MATCH')
                    AND (adjudication_outcome IS NULL
                         OR adjudication_outcome IN ('TRUE_MATCH', 'INCONCLUSIVE')))
              )
            """)
    List<ScreeningCheck> findBlocking(@Param("profileId") UUID profileId);

    /**
     * Returns matches waiting for a person to adjudicate them, oldest first.
     *
     * <pre>{@code
     * SELECT *
     * FROM screening_checks
     * WHERE result IN ('POTENTIAL_MATCH', 'CONFIRMED_MATCH')
     *   AND adjudicated_at IS NULL
     * ORDER BY screened_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Backs the analyst queue. Oldest first because an unadjudicated match is a host who cannot
     * trade.</p>
     *
     * @param batchSize maximum number of matches to return
     * @return possibly empty adjudication queue
     */
    @Query("""
            SELECT *
            FROM screening_checks
            WHERE result IN ('POTENTIAL_MATCH', 'CONFIRMED_MATCH')
              AND adjudicated_at IS NULL
            ORDER BY screened_at
            LIMIT :batchSize
            """)
    List<ScreeningCheck> findAwaitingAdjudication(@Param("batchSize") int batchSize);

    /**
     * Returns subjects due to be screened again.
     *
     * <pre>{@code
     * SELECT *
     * FROM screening_checks
     * WHERE next_screening_due_at IS NOT NULL
     *   AND next_screening_due_at <= :decisionInstant
     * ORDER BY next_screening_due_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>A clear result is only meaningful against the list as it stood when it ran, so screening has
     * to be repeated for as long as the host keeps selling.</p>
     *
     * @param decisionInstant the sweep's single decision instant
     * @param batchSize maximum number of subjects to return
     * @return possibly empty list of re-screening work, most overdue first
     */
    @Query("""
            SELECT *
            FROM screening_checks
            WHERE next_screening_due_at IS NOT NULL
              AND next_screening_due_at <= :decisionInstant
            ORDER BY next_screening_due_at
            LIMIT :batchSize
            """)
    List<ScreeningCheck> findDueForRescreening(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);
}
