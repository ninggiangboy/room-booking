package dev.ngb.backend.trust.internal.repository.signal;

import dev.ngb.backend.trust.internal.model.signal.RiskVelocityContribution;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.trust.internal.model.signal.RiskVelocityContribution;


/**
 * Reads what moved a velocity counter.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_velocity_contributions}.</p>
 */
public interface RiskVelocityContributionRepository extends ListCrudRepository<RiskVelocityContribution, UUID> {

    /**
     * Finds whether one source event has already been counted.
     *
     * <pre>{@code
     * SELECT * FROM risk_velocity_contributions
     * WHERE risk_velocity_counter_id = :riskVelocityCounterId AND source_event_id = :sourceEventId
     * }</pre>
     *
     * <p>Matches {@code uk_risk_velocity_contributions_event}, which is what makes a redelivery increment
     * nothing.</p>
     *
     * @param riskVelocityCounterId the counter
     * @param sourceEventId the event
     * @return the contribution, when the event was already applied
     */
    Optional<RiskVelocityContribution> findByRiskVelocityCounterIdAndSourceEventId(
            UUID riskVelocityCounterId, String sourceEventId);

    /**
     * Reads everything that moved one counter, so it can be rebuilt rather than trusted.
     *
     * <pre>{@code
     * SELECT * FROM risk_velocity_contributions WHERE risk_velocity_counter_id = :riskVelocityCounterId
     * ORDER BY event_time
     * }</pre>
     *
     * @param riskVelocityCounterId the counter
     * @return possibly empty list, oldest first
     */
    List<RiskVelocityContribution> findByRiskVelocityCounterIdOrderByEventTime(UUID riskVelocityCounterId);
}
