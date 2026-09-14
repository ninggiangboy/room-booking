package dev.ngb.backend.hostops.internal.repository.forecast;

import dev.ngb.backend.hostops.internal.model.forecast.CalendarValueSource;
import dev.ngb.backend.hostops.internal.model.forecast.CalendarValueKind;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.hostops.internal.model.forecast.CalendarValueKind;
import dev.ngb.backend.hostops.internal.model.forecast.CalendarValueSource;


/**
 * Reads which layer set the value a host sees on one night, and which layer it beat.
 *
 * <p>One row per resource, night and value, so the answer to "why does this night say this" is a
 * read rather than a re-run of the resolver against inputs that have since moved.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code calendar_value_sources}.</p>
 */
public interface CalendarValueSourceRepository extends ListCrudRepository<CalendarValueSource, UUID> {

    /**
     * Lists every resolved value on one night.
     *
     * @param inventoryResourceId the inventory resource
     * @param stayDate the night
     * @return possibly empty list
     */
    List<CalendarValueSource> findByInventoryResourceIdAndStayDate(UUID inventoryResourceId,
            LocalDate stayDate);

    /**
     * Finds the source of one value on one night.
     *
     * @param inventoryResourceId the inventory resource
     * @param stayDate the night
     * @param valueKind which calendar value
     * @return the resolution, when one has been computed
     */
    Optional<CalendarValueSource> findByInventoryResourceIdAndStayDateAndValueKind(
            UUID inventoryResourceId, LocalDate stayDate, CalendarValueKind valueKind);

    /**
     * Lists one value across a span of nights, which is what a calendar month renders from.
     *
     * <pre>{@code
     * SELECT * FROM calendar_value_sources
     * WHERE inventory_resource_id = :inventoryResourceId
     *   AND value_kind = :valueKind
     *   AND stay_date >= :fromDate
     *   AND stay_date < :untilDate
     * ORDER BY stay_date
     * }</pre>
     *
     * @param inventoryResourceId the inventory resource
     * @param valueKind which calendar value
     * @param fromDate first night to include
     * @param untilDate day after the last night to include
     * @return possibly empty list, earliest night first
     */
    @Query("""
            SELECT * FROM calendar_value_sources
            WHERE inventory_resource_id = :inventoryResourceId
              AND value_kind = :valueKind
              AND stay_date >= :fromDate
              AND stay_date < :untilDate
            ORDER BY stay_date
            """)
    List<CalendarValueSource> findSpan(
            @Param("inventoryResourceId") UUID inventoryResourceId,
            @Param("valueKind") String valueKind, @Param("fromDate") LocalDate fromDate,
            @Param("untilDate") LocalDate untilDate);

    /**
     * Lists the nights the host cannot edit, with the reason, which is what the calendar shows
     * when a host asks why a cell is greyed out.
     *
     * <pre>{@code
     * SELECT * FROM calendar_value_sources
     * WHERE inventory_resource_id = :inventoryResourceId
     *   AND NOT host_editable
     *   AND stay_date >= :fromDate
     * ORDER BY stay_date
     * }</pre>
     *
     * @param inventoryResourceId the inventory resource
     * @param fromDate first night to include
     * @return possibly empty list, earliest night first
     */
    @Query("""
            SELECT * FROM calendar_value_sources
            WHERE inventory_resource_id = :inventoryResourceId
              AND NOT host_editable
              AND stay_date >= :fromDate
            ORDER BY stay_date
            """)
    List<CalendarValueSource> findLocked(
            @Param("inventoryResourceId") UUID inventoryResourceId,
            @Param("fromDate") LocalDate fromDate);
}
