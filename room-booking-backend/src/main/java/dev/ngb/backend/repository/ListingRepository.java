package dev.ngb.backend.repository;

import dev.ngb.backend.model.Listing;
import dev.ngb.backend.model.ListingStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the public presentation of accommodation types.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code listings}. Nothing here reports availability: a published listing with no
 * sellable inventory for the requested nights is a normal state, and inventory is asked separately.</p>
 */
public interface ListingRepository extends ListCrudRepository<Listing, UUID> {

    /**
     * Finds a listing by its operational reference.
     *
     * <p>Spring derives {@code WHERE reference_code = ?}, matching {@code uk_listings_reference}.</p>
     *
     * @param referenceCode operational reference
     * @return the listing when the reference is known
     */
    Optional<Listing> findByReferenceCode(String referenceCode);

    /**
     * Finds a listing by its public URL identifier.
     *
     * <p>Spring derives {@code WHERE slug = ?}, matching {@code uk_listings_slug}. Returns the
     * listing whatever its status, so a caller can distinguish "no such listing" from "this listing
     * is no longer offered" rather than showing the same page for both.</p>
     *
     * @param slug URL-facing identifier
     * @return the listing when the slug is known
     */
    Optional<Listing> findBySlug(String slug);

    /**
     * Finds the live listing for an accommodation type, if there is one.
     *
     * <pre>{@code
     * SELECT *
     * FROM listings
     * WHERE accommodation_type_id = :accommodationTypeId
     *   AND status IN ('PUBLISHED', 'PAUSED', 'IN_REVIEW')
     * }</pre>
     *
     * <p>{@code uk_listings_one_live_per_type} guarantees at most one row matches, which is what
     * stops two published listings competing for the same nights and double-counting in search.</p>
     *
     * @param accommodationTypeId category whose listing is wanted
     * @return the live listing when one exists
     */
    @Query("""
            SELECT *
            FROM listings
            WHERE accommodation_type_id = :accommodationTypeId
              AND status IN ('PUBLISHED', 'PAUSED', 'IN_REVIEW')
            """)
    Optional<Listing> findLiveForAccommodationType(
            @Param("accommodationTypeId") UUID accommodationTypeId);

    /**
     * Returns listings in one status, most recently published first.
     *
     * <p>Spring derives {@code WHERE status = ? ORDER BY published_at DESC}. Used by operator review
     * queues rather than by guest-facing search, which has its own ranked path.</p>
     *
     * @param status status to filter by
     * @return possibly empty list of listings
     */
    List<Listing> findAllByStatusOrderByPublishedAtDesc(ListingStatus status);
}
