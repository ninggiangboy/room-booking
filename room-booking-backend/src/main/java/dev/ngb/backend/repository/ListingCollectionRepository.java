package dev.ngb.backend.repository;

import dev.ngb.backend.model.ListingCollection;
import dev.ngb.backend.model.ListingCollectionState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the wish lists guests keep.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code listing_collections}.</p>
 */
public interface ListingCollectionRepository extends ListCrudRepository<ListingCollection, UUID> {

    /**
     * Lists one guest's lists in one state.
     *
     * @param accountHolderId the guest
     * @param state where the lists stand
     * @return possibly empty list
     */
    List<ListingCollection> findByAccountHolderIdAndState(UUID accountHolderId,
            ListingCollectionState state);

    /**
     * Finds a list somebody opened by its sharing link.
     *
     * @param shareTokenDigest digest of the link
     * @return the list, when the link matches one
     */
    Optional<ListingCollection> findByShareTokenDigest(String shareTokenDigest);

    /**
     * Finds one guest's active list by the name they gave it.
     *
     * <pre>{@code
     * SELECT * FROM listing_collections
     * WHERE account_holder_id = :accountHolderId AND title = :title AND state = 'ACTIVE'
     * }</pre>
     *
     * @param accountHolderId the guest
     * @param title what they called it
     * @return the list, when they have one by that name
     */
    @Query("""
            SELECT * FROM listing_collections
            WHERE account_holder_id = :accountHolderId AND title = :title AND state = 'ACTIVE'
            """)
    Optional<ListingCollection> findActiveByTitle(
            @Param("accountHolderId") UUID accountHolderId, @Param("title") String title);
}
