package dev.ngb.backend.hostops.internal.repository.metric;

import dev.ngb.backend.hostops.internal.model.metric.HostPerformanceMetric;
import dev.ngb.backend.hostops.internal.model.metric.HostMetricSubjectKind;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.hostops.internal.model.metric.HostMetricSubjectKind;
import dev.ngb.backend.hostops.internal.model.metric.HostPerformanceMetric;


/**
 * Reads the computed figures shown to a host about one subject over one period.
 *
 * <p>Rows are append-only, so a subject and period can carry several computations and the newest
 * is the one to show. The older ones are what a host is owed when they ask why last month's
 * screen said something else.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code host_performance_metrics}.</p>
 */
public interface HostPerformanceMetricRepository extends ListCrudRepository<HostPerformanceMetric, UUID> {

    /**
     * Lists every computation for one subject, newest first.
     *
     * @param subjectKind what the figures are about
     * @param subjectId the subject
     * @return possibly empty list, most recently computed first
     */
    List<HostPerformanceMetric> findBySubjectKindAndSubjectIdOrderByComputedAtDesc(
            HostMetricSubjectKind subjectKind, UUID subjectId);

    /**
     * Finds the figure to show now: the newest computation of one metric for one subject over one
     * period.
     *
     * <pre>{@code
     * SELECT * FROM host_performance_metrics
     * WHERE publication_id = :publicationId
     *   AND subject_kind = :subjectKind
     *   AND subject_id = :subjectId
     *   AND period_start = :periodStart
     * ORDER BY computed_at DESC
     * LIMIT 1
     * }</pre>
     *
     * @param publicationId the published metric
     * @param subjectKind what the figure is about
     * @param subjectId the subject
     * @param periodStart first day of the period
     * @return the newest computation, when there is one
     */
    @Query("""
            SELECT * FROM host_performance_metrics
            WHERE publication_id = :publicationId
              AND subject_kind = :subjectKind
              AND subject_id = :subjectId
              AND period_start = :periodStart
            ORDER BY computed_at DESC
            LIMIT 1
            """)
    Optional<HostPerformanceMetric> findCurrent(@Param("publicationId") UUID publicationId,
            @Param("subjectKind") String subjectKind, @Param("subjectId") UUID subjectId,
            @Param("periodStart") LocalDate periodStart);

    /**
     * Lists the figures the host was not shown a number for, which is what a review of the
     * evidence floors has to read before anybody proposes lowering one.
     *
     * <pre>{@code
     * SELECT * FROM host_performance_metrics
     * WHERE evidence_state <> 'SUFFICIENT' AND computed_at >= :since
     * ORDER BY computed_at DESC
     * }</pre>
     *
     * @param since earliest computation instant to include
     * @return possibly empty list, most recently computed first
     */
    @Query("""
            SELECT * FROM host_performance_metrics
            WHERE evidence_state <> 'SUFFICIENT' AND computed_at >= :since
            ORDER BY computed_at DESC
            """)
    List<HostPerformanceMetric> findWithoutValue(@Param("since") Instant since);
}
