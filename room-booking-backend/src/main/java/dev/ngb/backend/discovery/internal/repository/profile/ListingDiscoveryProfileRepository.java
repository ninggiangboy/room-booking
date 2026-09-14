package dev.ngb.backend.discovery.internal.repository.profile;

import dev.ngb.backend.discovery.internal.model.profile.ListingDiscoveryProfile;
import dev.ngb.backend.review.types.DerivedProfileStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.discovery.internal.model.profile.ListingDiscoveryProfile;
import dev.ngb.backend.review.types.DerivedProfileStatus;


/**
 * Reads the versioned listing read model discovery serves from.
 *
 * <p>Availability and trip price are deliberately absent: those are request-time facts owned by
 * inventory and pricing, and a profile that cached them would be wrong by the next day.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code listing_discovery_profiles}.</p>
 */
public interface ListingDiscoveryProfileRepository extends ListCrudRepository<ListingDiscoveryProfile, UUID> {

    /**
     * Finds the current profile for one listing.
     *
     * @param listingId the listing
     * @param status normally {@code CURRENT}
     * @return the profile, when one has been built
     */
    Optional<ListingDiscoveryProfile> findByListingIdAndStatus(UUID listingId,
            DerivedProfileStatus status);

    /**
     * Loads the current profiles for a candidate set in one round trip.
     *
     * <pre>{@code
     * SELECT * FROM listing_discovery_profiles
     * WHERE listing_id = ANY (:listingIds) AND status = 'CURRENT'
     * }</pre>
     *
     * <p>A missing profile is an ordinary outcome, not an error. Search degrades to conservative priors
     * rather than failing.</p>
     *
     * @param listingIds the candidate listings
     * @return possibly empty list; a listing with no profile is enriched from stored facts and priors instead
     */
    @Query("""
            SELECT * FROM listing_discovery_profiles
            WHERE listing_id = ANY (:listingIds) AND status = 'CURRENT'
            """)
    List<ListingDiscoveryProfile> findCurrentForListings(@Param("listingIds") UUID[] listingIds);

    /**
     * Lists current profiles past their freshness deadline, for the refresh worker.
     *
     * <pre>{@code
     * SELECT * FROM listing_discovery_profiles
     * WHERE status = 'CURRENT' AND expires_at <= :at
     * ORDER BY expires_at
     * LIMIT :limit
     * }</pre>
     *
     * @param at instant to compare against
     * @param limit batch size
     * @return possibly empty list, most stale first
     */
    @Query("""
            SELECT * FROM listing_discovery_profiles
            WHERE status = 'CURRENT' AND expires_at <= :at
            ORDER BY expires_at
            LIMIT :limit
            """)
    List<ListingDiscoveryProfile> findStale(@Param("at") Instant at, @Param("limit") int limit);
}
