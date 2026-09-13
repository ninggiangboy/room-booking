package dev.ngb.backend.repository;

import dev.ngb.backend.model.RiskVelocityCounter;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and locks velocity counters.
 *
 * <p>A strong transaction limit is enforced by locking the counter row, not by hoping two requests do
 * not arrive together, which is what the locking read below exists for.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_velocity_counters}.</p>
 */
public interface RiskVelocityCounterRepository extends ListCrudRepository<RiskVelocityCounter, UUID> {

    /**
     * Finds one counter window.
     *
     * <pre>{@code
     * SELECT * FROM risk_velocity_counters
     * WHERE rule_key = :ruleKey
     *   AND dimension_digest = :dimensionDigest
     *   AND window_start = :windowStart
     *   AND window_end = :windowEnd
     * }</pre>
     *
     * @param ruleKey velocity rule
     * @param dimensionDigest digest of the dimension values
     * @param windowStart start of the window
     * @param windowEnd end of it
     * @return the counter, when it exists
     */
    Optional<RiskVelocityCounter> findByRuleKeyAndDimensionDigestAndWindowStartAndWindowEnd(
            String ruleKey, String dimensionDigest, Instant windowStart, Instant windowEnd);

    /**
     * Locks one counter before a limit is enforced against it.
     *
     * <pre>{@code
     * SELECT * FROM risk_velocity_counters WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Requires an active transaction. Two requests reading the same budget without this both see room
     * and both proceed.</p>
     *
     * @param id counter
     * @return the locked counter, when it exists
     */
    @Query("SELECT * FROM risk_velocity_counters WHERE id = :id FOR UPDATE")
    Optional<RiskVelocityCounter> findByIdForUpdate(@Param("id") UUID id);
}
