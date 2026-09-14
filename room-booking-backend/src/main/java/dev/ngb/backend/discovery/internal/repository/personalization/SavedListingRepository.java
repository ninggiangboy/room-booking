package dev.ngb.backend.discovery.internal.repository.personalization;

import dev.ngb.backend.discovery.internal.model.personalization.SavedListing;
import dev.ngb.backend.discovery.internal.model.personalization.SavedListingState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes the listings a guest has explicitly saved.
 *
 * <p>An explicit save is the strongest cheap preference signal there is, so it is authoritative state
 * rather than something inferred from the event stream. Unsaving keeps the row.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code saved_listings}.</p>
 */
public interface SavedListingRepository extends ListCrudRepository<SavedListing, UUID> {

    /**
     * Finds the save row for one guest and listing, whatever state it is in.
     *
     * @param accountHolderId the guest
     * @param listingId the listing
     * @return the row, when the guest has ever saved this listing
     */
    Optional<SavedListing> findByAccountHolderIdAndListingId(UUID accountHolderId, UUID listingId);

    /**
     * Lists what one guest currently has saved, most recent first.
     *
     * @param accountHolderId the guest
     * @param state normally {@code SAVED}
     * @return possibly empty list, most recently saved first
     */
    List<SavedListing> findByAccountHolderIdAndStateOrderBySavedAtDesc(UUID accountHolderId,
            SavedListingState state);

    /**
     * Counts current saves of one listing, for the host-facing interest signal.
     *
     * <pre>{@code
     * SELECT count(*) FROM saved_listings WHERE listing_id = :listingId AND state = 'SAVED'
     * }</pre>
     *
     * @param listingId the listing
     * @return how many guests currently have it saved
     */
    @Query("SELECT count(*) FROM saved_listings WHERE listing_id = :listingId AND state = 'SAVED'")
    long countCurrentSaves(@Param("listingId") UUID listingId);
}
