package dev.ngb.backend.repository;

import dev.ngb.backend.model.HostResponseMetric;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads how responsive, accepting and reliable a host was over one period.
 *
 * <p>Each row carries the counts its rates were computed from, so a support agent looking at a
 * disputed figure reads the same arithmetic the host does.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code host_response_metrics}.</p>
 */
public interface HostResponseMetricRepository extends ListCrudRepository<HostResponseMetric, UUID> {

    /**
     * Lists every computation for one host, newest first.
     *
     * @param hostAccountHolderId the host
     * @return possibly empty list, most recently computed first
     */
    List<HostResponseMetric> findByHostAccountHolderIdOrderByComputedAtDesc(
            UUID hostAccountHolderId);

    /**
     * Finds the figures to show now for one host and period.
     *
     * <pre>{@code
     * SELECT * FROM host_response_metrics
     * WHERE host_account_holder_id = :hostAccountHolderId AND period_start = :periodStart
     * ORDER BY computed_at DESC
     * LIMIT 1
     * }</pre>
     *
     * @param hostAccountHolderId the host
     * @param periodStart first day of the period
     * @return the newest computation, when there is one
     */
    @Query("""
            SELECT * FROM host_response_metrics
            WHERE host_account_holder_id = :hostAccountHolderId AND period_start = :periodStart
            ORDER BY computed_at DESC
            LIMIT 1
            """)
    Optional<HostResponseMetric> findCurrent(
            @Param("hostAccountHolderId") UUID hostAccountHolderId,
            @Param("periodStart") LocalDate periodStart);

    /**
     * Lists the hosts who are not in good standing, which is the queue a supply team works.
     *
     * <pre>{@code
     * SELECT * FROM host_response_metrics
     * WHERE compliance_state <> 'IN_GOOD_STANDING' AND period_start = :periodStart
     * ORDER BY policy_breaches_recorded DESC, host_cancellation_rate DESC NULLS LAST
     * }</pre>
     *
     * @param periodStart first day of the period
     * @return possibly empty list, most breaches first
     */
    @Query("""
            SELECT * FROM host_response_metrics
            WHERE compliance_state <> 'IN_GOOD_STANDING' AND period_start = :periodStart
            ORDER BY policy_breaches_recorded DESC, host_cancellation_rate DESC NULLS LAST
            """)
    List<HostResponseMetric> findOutOfStanding(@Param("periodStart") LocalDate periodStart);
}
