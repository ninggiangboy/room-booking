package dev.ngb.backend.growth.internal.repository.program;

import dev.ngb.backend.growth.internal.model.program.GrowthProgramVersion;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the published terms a growth programme has run under.
 *
 * <p>Rows are frozen once they leave draft, so the version cited by a reward or an eligibility
 * decision still says what it said at the time.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code growth_program_versions}.</p>
 */
public interface GrowthProgramVersionRepository extends ListCrudRepository<GrowthProgramVersion, UUID> {

    /**
     * Finds one numbered version of a programme.
     *
     * @param growthProgramId the programme
     * @param versionNumber position within the programme
     * @return the version, when it exists
     */
    Optional<GrowthProgramVersion> findByGrowthProgramIdAndVersionNumber(UUID growthProgramId,
            int versionNumber);

    /**
     * Lists every version of a programme, newest first.
     *
     * @param growthProgramId the programme
     * @return possibly empty list, highest version first
     */
    List<GrowthProgramVersion> findByGrowthProgramIdOrderByVersionNumberDesc(
            UUID growthProgramId);

    /**
     * Finds the terms in force for a programme at one instant, which is what a new participant
     * qualifies under.
     *
     * <pre>{@code
     * SELECT * FROM growth_program_versions
     * WHERE growth_program_id = :growthProgramId
     *   AND status = 'ACTIVE'
     *   AND effective_from <= :at
     *   AND (effective_until IS NULL OR effective_until > :at)
     * ORDER BY version_number DESC
     * LIMIT 1
     * }</pre>
     *
     * @param growthProgramId the programme
     * @param at instant the terms are needed for
     * @return the terms in force, when there are any
     */
    @Query("""
            SELECT * FROM growth_program_versions
            WHERE growth_program_id = :growthProgramId
              AND status = 'ACTIVE'
              AND effective_from <= :at
              AND (effective_until IS NULL OR effective_until > :at)
            ORDER BY version_number DESC
            LIMIT 1
            """)
    Optional<GrowthProgramVersion> findInForce(@Param("growthProgramId") UUID growthProgramId,
            @Param("at") Instant at);

    /**
     * Lists the published versions that grant credit, which is the set a liability forecast of
     * outstanding credit has to read.
     *
     * <pre>{@code
     * SELECT * FROM growth_program_versions
     * WHERE reward_kind = 'CREDIT' AND status IN ('ACTIVE', 'DEPRECATED')
     * ORDER BY effective_from DESC
     * }</pre>
     *
     * @return possibly empty list, most recently effective first
     */
    @Query("""
            SELECT * FROM growth_program_versions
            WHERE reward_kind = 'CREDIT' AND status IN ('ACTIVE', 'DEPRECATED')
            ORDER BY effective_from DESC
            """)
    List<GrowthProgramVersion> findCreditGranting();
}
