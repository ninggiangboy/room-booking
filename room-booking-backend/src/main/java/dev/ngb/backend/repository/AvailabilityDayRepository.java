package dev.ngb.backend.repository;

import dev.ngb.backend.model.AvailabilityDay;
import dev.ngb.backend.model.AvailabilityDayId;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reads and locks the per-night calendar that pooled inventory is sold from.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} operate on the composite
 * {@link AvailabilityDayId} key and the {@code availability_days} table.</p>
 */
public interface AvailabilityDayRepository
        extends ListCrudRepository<AvailabilityDay, AvailabilityDayId> {

    /**
     * Reads a resource's nights over a range, without locking.
     *
     * <pre>{@code
     * SELECT *
     * FROM availability_days
     * WHERE inventory_resource_id = :resourceId
     *   AND stay_date >= :checkIn
     *   AND stay_date < :checkOut
     * ORDER BY stay_date
     * }</pre>
     *
     * <p>The upper bound is exclusive because a stay occupies the nights from check-in up to but not
     * including checkout. Use this for search and display only: what it returns is a snapshot another
     * transaction may already be consuming.</p>
     *
     * @param resourceId inventory resource being read
     * @param checkIn first night, inclusive
     * @param checkOut departure date, exclusive
     * @return possibly empty list of nights in date order
     */
    @Query("""
            SELECT *
            FROM availability_days
            WHERE inventory_resource_id = :resourceId
              AND stay_date >= :checkIn
              AND stay_date < :checkOut
            ORDER BY stay_date
            """)
    List<AvailabilityDay> findRange(
            @Param("resourceId") UUID resourceId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut);

    /**
     * Locks a resource's nights over a range, in a stable order, for the booking commit.
     *
     * <pre>{@code
     * SELECT *
     * FROM availability_days
     * WHERE inventory_resource_id = :resourceId
     *   AND stay_date >= :checkIn
     *   AND stay_date < :checkOut
     * ORDER BY stay_date
     * FOR UPDATE
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction, and is the first step of the booking commit.</strong>
     * Two things matter here. Locking at all is what stops two transactions each reading capacity the
     * other is about to consume. Locking <em>in date order</em> is what stops two transactions whose
     * stays overlap from taking the same rows in opposite orders and deadlocking — every caller must
     * use this method rather than locking rows ad hoc.</p>
     *
     * <p>The lock makes the read trustworthy; the database's capacity constraint is still what
     * ultimately refuses an overselling write.</p>
     *
     * @param resourceId inventory resource being committed against
     * @param checkIn first night, inclusive
     * @param checkOut departure date, exclusive
     * @return the locked nights in date order
     */
    @Query("""
            SELECT *
            FROM availability_days
            WHERE inventory_resource_id = :resourceId
              AND stay_date >= :checkIn
              AND stay_date < :checkOut
            ORDER BY stay_date
            FOR UPDATE
            """)
    List<AvailabilityDay> lockRangeForUpdate(
            @Param("resourceId") UUID resourceId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut);

    /**
     * Adds to the held count across a stay, failing if any night would be oversold.
     *
     * <pre>{@code
     * UPDATE availability_days
     * SET held_quantity = held_quantity + :quantity,
     *     updated_at = :decisionInstant
     * WHERE inventory_resource_id = :resourceId
     *   AND stay_date >= :checkIn
     *   AND stay_date < :checkOut
     * }</pre>
     *
     * <p>No capacity check appears in the {@code WHERE} clause on purpose. The database's
     * {@code ck_availability_days_capacity} constraint refuses the statement outright if any night
     * would exceed its sellable quantity, so an overselling attempt fails loudly rather than silently
     * updating fewer rows than intended and leaving the caller to notice.</p>
     *
     * <p>The returned count must equal the number of nights in the stay; anything less means the
     * calendar has not been materialized that far ahead.</p>
     *
     * @param resourceId inventory resource being held
     * @param checkIn first night, inclusive
     * @param checkOut departure date, exclusive
     * @param quantity how many to hold per night
     * @param decisionInstant the command's single decision instant
     * @return number of nights updated
     */
    @Modifying
    @Query("""
            UPDATE availability_days
            SET held_quantity = held_quantity + :quantity,
                updated_at = :decisionInstant
            WHERE inventory_resource_id = :resourceId
              AND stay_date >= :checkIn
              AND stay_date < :checkOut
            """)
    int addHeldQuantity(
            @Param("resourceId") UUID resourceId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("quantity") int quantity,
            @Param("decisionInstant") java.time.Instant decisionInstant);

    /**
     * Moves quantity from held to booked across a stay, as a hold becomes a confirmed booking.
     *
     * <pre>{@code
     * UPDATE availability_days
     * SET held_quantity = held_quantity - :quantity,
     *     booked_quantity = booked_quantity + :quantity,
     *     updated_at = :decisionInstant
     * WHERE inventory_resource_id = :resourceId
     *   AND stay_date >= :checkIn
     *   AND stay_date < :checkOut
     * }</pre>
     *
     * <p>Both counters move in one statement so the total never dips. Releasing the hold first and
     * booking second would open a window in which the nights appear free, and another guest could
     * take them between the two writes.</p>
     *
     * @param resourceId inventory resource being confirmed
     * @param checkIn first night, inclusive
     * @param checkOut departure date, exclusive
     * @param quantity how many to convert per night
     * @param decisionInstant the command's single decision instant
     * @return number of nights updated
     */
    @Modifying
    @Query("""
            UPDATE availability_days
            SET held_quantity = held_quantity - :quantity,
                booked_quantity = booked_quantity + :quantity,
                updated_at = :decisionInstant
            WHERE inventory_resource_id = :resourceId
              AND stay_date >= :checkIn
              AND stay_date < :checkOut
            """)
    int convertHeldToBooked(
            @Param("resourceId") UUID resourceId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("quantity") int quantity,
            @Param("decisionInstant") java.time.Instant decisionInstant);

    /**
     * Releases held quantity across a stay, when a hold expires or is given up.
     *
     * <pre>{@code
     * UPDATE availability_days
     * SET held_quantity = held_quantity - :quantity,
     *     updated_at = :decisionInstant
     * WHERE inventory_resource_id = :resourceId
     *   AND stay_date >= :checkIn
     *   AND stay_date < :checkOut
     *   AND held_quantity >= :quantity
     * }</pre>
     *
     * <p>The {@code held_quantity >= :quantity} guard is what makes a double release harmless: a
     * sweeper and a client both releasing the same hold would otherwise drive the counter negative,
     * and the returned count lets the caller see that nothing was released.</p>
     *
     * @param resourceId inventory resource being released
     * @param checkIn first night, inclusive
     * @param checkOut departure date, exclusive
     * @param quantity how many to release per night
     * @param decisionInstant the command's single decision instant
     * @return number of nights updated
     */
    @Modifying
    @Query("""
            UPDATE availability_days
            SET held_quantity = held_quantity - :quantity,
                updated_at = :decisionInstant
            WHERE inventory_resource_id = :resourceId
              AND stay_date >= :checkIn
              AND stay_date < :checkOut
              AND held_quantity >= :quantity
            """)
    int releaseHeldQuantity(
            @Param("resourceId") UUID resourceId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("quantity") int quantity,
            @Param("decisionInstant") java.time.Instant decisionInstant);
}
