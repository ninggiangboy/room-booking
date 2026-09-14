package dev.ngb.backend.admin.internal.repository.configuration;

import dev.ngb.backend.admin.internal.model.configuration.ConfigurationRollout;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads how an approved change actually reached production.
 *
 * <p>A change that reached ten per cent of one market and was stopped is not the same event as one
 * that reached everybody, and this is where the difference is kept.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code configuration_rollouts}.</p>
 */
public interface ConfigurationRolloutRepository extends ListCrudRepository<ConfigurationRollout, UUID> {

    /**
     * Lists the stages of one change, in order.
     *
     * @param changeRequestId the request
     * @return possibly empty list, in stage order
     */
    List<ConfigurationRollout> findByChangeRequestIdOrderByStageSequence(UUID changeRequestId);

    /**
     * Lists the rollouts still running, which is what a deployment view watches.
     *
     * <pre>{@code
     * SELECT * FROM configuration_rollouts
     * WHERE rollout_state = 'RUNNING'
     * ORDER BY started_at
     * }</pre>
     *
     * @return possibly empty list, longest running first
     */
    @Query("""
            SELECT * FROM configuration_rollouts
            WHERE rollout_state = 'RUNNING'
            ORDER BY started_at
            """)
    List<ConfigurationRollout> findRunning();

    /**
     * Lists the staged rollouts whose observation window has elapsed, which is what a promotion job
     * reads before widening anything.
     *
     * <pre>{@code
     * SELECT * FROM configuration_rollouts
     * WHERE rollout_state = 'RUNNING' AND observation_window_minutes IS NOT NULL
     *   AND started_at + make_interval(mins => observation_window_minutes) <= :asOf
     * ORDER BY started_at
     * }</pre>
     *
     * @param asOf the instant to measure against
     * @return possibly empty list, longest watched first
     */
    @Query("""
            SELECT * FROM configuration_rollouts
            WHERE rollout_state = 'RUNNING' AND observation_window_minutes IS NOT NULL
              AND started_at + make_interval(mins => observation_window_minutes) <= :asOf
            ORDER BY started_at
            """)
    List<ConfigurationRollout> findObservationElapsed(@Param("asOf") Instant asOf);

    /**
     * Lists the rollouts that were stopped or reversed, which is the change-failure rate a
     * governance report is actually asking for.
     *
     * <pre>{@code
     * SELECT * FROM configuration_rollouts
     * WHERE rollout_state IN ('ABORTED', 'ROLLED_BACK')
     *   AND started_at >= :from AND started_at < :to
     * ORDER BY started_at
     * }</pre>
     *
     * @param from start of the window, inclusive
     * @param to end of the window, exclusive
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM configuration_rollouts
            WHERE rollout_state IN ('ABORTED', 'ROLLED_BACK')
              AND started_at >= :from AND started_at < :to
            ORDER BY started_at
            """)
    List<ConfigurationRollout> findUnsuccessfulBetween(@Param("from") Instant from,
            @Param("to") Instant to);
}
