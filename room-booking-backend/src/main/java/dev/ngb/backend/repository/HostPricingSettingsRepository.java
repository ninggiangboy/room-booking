package dev.ngb.backend.repository;

import dev.ngb.backend.model.HostPricingSettings;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads a host's standing pricing instructions.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code host_pricing_settings}.</p>
 */
public interface HostPricingSettingsRepository extends ListCrudRepository<HostPricingSettings, UUID> {

    /**
     * Returns the instructions in force for an accommodation type at an instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM host_pricing_settings
     * WHERE accommodation_type_id = :accommodationTypeId
     *   AND effective_from <= :instant
     *   AND (effective_until IS NULL OR effective_until > :instant)
     * }</pre>
     *
     * <p>At most one row can match: the exclusion constraint forbids overlapping periods, so this
     * returns an {@code Optional} rather than a list and the absence of a row means the host has not
     * configured pricing rather than that the query was ambiguous.</p>
     *
     * <p>The instant is bound by the caller rather than read as {@code now()}, so one command's
     * decision instant governs every lookup it makes.</p>
     *
     * @param accommodationTypeId accommodation type being priced
     * @param instant the command's decision instant
     * @return the instructions in force, or empty when none are
     */
    @Query("""
            SELECT *
            FROM host_pricing_settings
            WHERE accommodation_type_id = :accommodationTypeId
              AND effective_from <= :instant
              AND (effective_until IS NULL OR effective_until > :instant)
            """)
    Optional<HostPricingSettings> findInForce(
            @Param("accommodationTypeId") UUID accommodationTypeId,
            @Param("instant") Instant instant);

    /**
     * Returns every settings row an accommodation type has had, newest first.
     *
     * <p>Spring derives {@code WHERE accommodation_type_id = ? ORDER BY effective_from DESC}. Used to
     * explain why a past price was allowed, which needs the bounds as they stood then.</p>
     *
     * @param accommodationTypeId accommodation type whose history is wanted
     * @return possibly empty list, most recent period first
     */
    List<HostPricingSettings> findAllByAccommodationTypeIdOrderByEffectiveFromDesc(
            UUID accommodationTypeId);
}
