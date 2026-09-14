package dev.ngb.backend.hostops.internal.repository.benchmark;

import dev.ngb.backend.hostops.internal.model.benchmark.MarketBenchmarkAggregate;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what a cohort of hosts looked like over one period, or the recorded reason there is no
 * answer.
 *
 * <p>Suppressed rows are returned like any other, because a host shown nothing cannot tell a
 * privacy floor from a broken pipeline.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code market_benchmark_aggregates}.</p>
 */
public interface MarketBenchmarkAggregateRepository extends ListCrudRepository<MarketBenchmarkAggregate, UUID> {

    /**
     * Finds the benchmark to show now for one metric, cohort and period.
     *
     * <pre>{@code
     * SELECT * FROM market_benchmark_aggregates
     * WHERE publication_id = :publicationId
     *   AND cohort_definition_id = :cohortDefinitionId
     *   AND period_start = :periodStart
     * ORDER BY computed_at DESC
     * LIMIT 1
     * }</pre>
     *
     * @param publicationId the published metric
     * @param cohortDefinitionId the cohort
     * @param periodStart first day of the period
     * @return the newest computation, when there is one
     */
    @Query("""
            SELECT * FROM market_benchmark_aggregates
            WHERE publication_id = :publicationId
              AND cohort_definition_id = :cohortDefinitionId
              AND period_start = :periodStart
            ORDER BY computed_at DESC
            LIMIT 1
            """)
    Optional<MarketBenchmarkAggregate> findCurrent(@Param("publicationId") UUID publicationId,
            @Param("cohortDefinitionId") UUID cohortDefinitionId,
            @Param("periodStart") LocalDate periodStart);

    /**
     * Lists every computation for one cohort, newest first.
     *
     * @param cohortDefinitionId the cohort
     * @return possibly empty list, most recently computed first
     */
    List<MarketBenchmarkAggregate> findByCohortDefinitionIdOrderByComputedAtDesc(
            UUID cohortDefinitionId);

    /**
     * Lists the benchmarks that were suppressed, which is what a review of the cohort definitions
     * reads to find peer sets that are drawn too narrowly to ever publish.
     *
     * <pre>{@code
     * SELECT * FROM market_benchmark_aggregates
     * WHERE publication_state = 'SUPPRESSED' AND period_start = :periodStart
     * ORDER BY suppression_reason, cohort_definition_id
     * }</pre>
     *
     * @param periodStart first day of the period
     * @return possibly empty list, grouped by reason
     */
    @Query("""
            SELECT * FROM market_benchmark_aggregates
            WHERE publication_state = 'SUPPRESSED' AND period_start = :periodStart
            ORDER BY suppression_reason, cohort_definition_id
            """)
    List<MarketBenchmarkAggregate> findSuppressed(@Param("periodStart") LocalDate periodStart);
}
