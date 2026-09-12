package dev.ngb.backend.repository;

import dev.ngb.backend.model.ManualPriceOverride;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reads the host's direct price instructions.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code manual_price_overrides}.</p>
 */
public interface ManualPriceOverrideRepository extends ListCrudRepository<ManualPriceOverride, UUID> {

    /**
     * Returns the active overrides touching a date range.
     *
     * <pre>{@code
     * SELECT *
     * FROM manual_price_overrides
     * WHERE accommodation_type_id = :accommodationTypeId
     *   AND status = 'ACTIVE'
     *   AND stay_range && daterange(:from, :until, '[)')
     * ORDER BY lower(stay_range)
     * }</pre>
     *
     * <p>The probe range is built {@code '[)'} so it agrees with how overrides are stored: one ending
     * on the 5th does not touch one beginning on the 5th. Overlaps between active overrides are
     * impossible, so the results partition the range rather than competing over it.</p>
     *
     * @param accommodationTypeId accommodation type being priced
     * @param from first date of interest, inclusive
     * @param until first date past the interest, exclusive
     * @return possibly empty list, earliest range first
     */
    @Query("""
            SELECT *
            FROM manual_price_overrides
            WHERE accommodation_type_id = :accommodationTypeId
              AND status = 'ACTIVE'
              AND stay_range && daterange(:from, :until, '[)')
            ORDER BY lower(stay_range)
            """)
    List<ManualPriceOverride> findActiveOverlapping(
            @Param("accommodationTypeId") UUID accommodationTypeId,
            @Param("from") LocalDate from,
            @Param("until") LocalDate until);
}
