package dev.ngb.backend.growth.internal.repository.demand;

import dev.ngb.backend.growth.internal.model.demand.WaitlistEntry;
import dev.ngb.backend.platform.StayRange;
import dev.ngb.backend.growth.internal.model.demand.WaitlistEntryState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.growth.internal.model.demand.WaitlistEntry;
import dev.ngb.backend.growth.internal.model.demand.WaitlistEntryState;
import dev.ngb.backend.platform.StayRange;


/**
 * Reads who is waiting for something that is not currently available.
 *
 * <p>An entry only reaches an offer with a quote holding the inventory behind it, so this
 * repository never hands a worker somebody to tell about a room nobody is holding.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code waitlist_entries}.</p>
 */
public interface WaitlistEntryRepository extends ListCrudRepository<WaitlistEntry, UUID> {

    /**
     * Lists one guest's waitlist entries in one state.
     *
     * @param accountHolderId the guest
     * @param state where the entries stand
     * @return possibly empty list
     */
    List<WaitlistEntry> findByAccountHolderIdAndState(UUID accountHolderId,
            WaitlistEntryState state);

    /**
     * Lists the guests waiting on one listing for a stay, in queue order, which is who gets told
     * first when something opens.
     *
     * <pre>{@code
     * SELECT * FROM waitlist_entries
     * WHERE listing_id = :listingId
     *   AND stay_range && :stayRange
     *   AND state = 'WAITING'
     *   AND expires_at > :at
     * ORDER BY queue_position NULLS LAST, joined_at
     * }</pre>
     *
     * @param listingId the listing
     * @param stayRange the stay that opened up
     * @param at instant the room opened at
     * @return possibly empty list, in queue order
     */
    @Query("""
            SELECT * FROM waitlist_entries
            WHERE listing_id = :listingId
              AND stay_range && :stayRange
              AND state = 'WAITING'
              AND expires_at > :at
            ORDER BY queue_position NULLS LAST, joined_at
            """)
    List<WaitlistEntry> findQueue(@Param("listingId") UUID listingId,
            @Param("stayRange") StayRange stayRange, @Param("at") Instant at);

    /**
     * Lists the offers whose hold has lapsed, which is the set that goes back into the queue.
     *
     * <pre>{@code
     * SELECT * FROM waitlist_entries
     * WHERE state = 'OFFERED' AND offer_expires_at <= :at
     * ORDER BY offer_expires_at
     * }</pre>
     *
     * @param at instant the sweep is running for
     * @return possibly empty list, longest lapsed first
     */
    @Query("""
            SELECT * FROM waitlist_entries
            WHERE state = 'OFFERED' AND offer_expires_at <= :at
            ORDER BY offer_expires_at
            """)
    List<WaitlistEntry> findLapsedOffers(@Param("at") Instant at);

    /**
     * Lists the entries that have run out, which is the set that stops waiting.
     *
     * <pre>{@code
     * SELECT * FROM waitlist_entries
     * WHERE state IN ('WAITING', 'OFFERED') AND expires_at <= :at
     * ORDER BY expires_at
     * }</pre>
     *
     * @param at instant the sweep is running for
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT * FROM waitlist_entries
            WHERE state IN ('WAITING', 'OFFERED') AND expires_at <= :at
            ORDER BY expires_at
            """)
    List<WaitlistEntry> findExpirable(@Param("at") Instant at);
}
