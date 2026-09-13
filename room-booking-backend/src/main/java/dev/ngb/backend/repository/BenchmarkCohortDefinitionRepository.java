package dev.ngb.backend.repository;

import dev.ngb.backend.model.BenchmarkCohortDefinition;
import dev.ngb.backend.model.GovernedRegistryStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads how peer sets are drawn and the privacy floors they publish under.
 *
 * <p>A benchmark cites one of these rows, so this is where the question "how many hosts was that
 * median drawn from, and what was the floor at the time" is answered.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code benchmark_cohort_definitions}.</p>
 */
public interface BenchmarkCohortDefinitionRepository extends ListCrudRepository<BenchmarkCohortDefinition, UUID> {

    /**
     * Finds one exact version of a cohort.
     *
     * @param cohortKey the cohort
     * @param cohortVersion which version of it
     * @return the definition, when it is registered
     */
    Optional<BenchmarkCohortDefinition> findByCohortKeyAndCohortVersion(String cohortKey,
            int cohortVersion);

    /**
     * Lists the cohorts of one market in one lifecycle state.
     *
     * @param marketCode the market
     * @param status the lifecycle state
     * @return possibly empty list
     */
    List<BenchmarkCohortDefinition> findByMarketCodeAndStatus(String marketCode,
            GovernedRegistryStatus status);

    /**
     * Finds the cohort version a benchmark should be computed against: the active one, or the
     * newest of them if several were somehow left active.
     *
     * <pre>{@code
     * SELECT * FROM benchmark_cohort_definitions
     * WHERE cohort_key = :cohortKey AND status = 'ACTIVE'
     * ORDER BY cohort_version DESC
     * LIMIT 1
     * }</pre>
     *
     * @param cohortKey the cohort
     * @return the usable version, when one is active
     */
    @Query("""
            SELECT * FROM benchmark_cohort_definitions
            WHERE cohort_key = :cohortKey AND status = 'ACTIVE'
            ORDER BY cohort_version DESC
            LIMIT 1
            """)
    Optional<BenchmarkCohortDefinition> findUsable(@Param("cohortKey") String cohortKey);
}
