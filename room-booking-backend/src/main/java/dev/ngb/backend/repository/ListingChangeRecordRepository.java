package dev.ngb.backend.repository;

import dev.ngb.backend.model.ListingChangeRecord;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads what changed on a listing, and when.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select and insert SQL
 * for {@code listing_change_history}. The rows are never revised.</p>
 */
public interface ListingChangeRecordRepository
        extends ListCrudRepository<ListingChangeRecord, UUID> {

    /**
     * Returns a listing's change timeline, most recent first.
     *
     * <p>Spring derives {@code WHERE listing_id = ? ORDER BY occurred_at DESC}.</p>
     *
     * @param listingId listing whose history is wanted
     * @return possibly empty timeline, most recent first
     */
    List<ListingChangeRecord> findAllByListingIdOrderByOccurredAtDesc(UUID listingId);

    /**
     * Returns the changes made to a listing after a given instant, oldest first.
     *
     * <pre>{@code
     * SELECT *
     * FROM listing_change_history
     * WHERE listing_id = :listingId
     *   AND occurred_at > :since
     * ORDER BY occurred_at
     * }</pre>
     *
     * <p>This is the dispute query. A guest arguing "the listing said there was air conditioning"
     * needs what changed between the moment they booked and now — and a host editing the listing
     * after a complaint should not be able to make the original claim disappear.</p>
     *
     * @param listingId listing under dispute
     * @param since instant to look forward from, typically when the booking was made
     * @return possibly empty list of subsequent changes, oldest first
     */
    @Query("""
            SELECT *
            FROM listing_change_history
            WHERE listing_id = :listingId
              AND occurred_at > :since
            ORDER BY occurred_at
            """)
    List<ListingChangeRecord> findChangesSince(
            @Param("listingId") UUID listingId,
            @Param("since") Instant since);
}
