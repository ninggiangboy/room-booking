package dev.ngb.backend.repository;

import dev.ngb.backend.model.ListingDiscoveryFeature;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the features inside one listing profile version.
 *
 * <p>A feature absent from this table is unknown, and unknown contributes nothing either way. Callers
 * must not read a missing row as a zero.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code listing_discovery_features}.</p>
 */
public interface ListingDiscoveryFeatureRepository extends ListCrudRepository<ListingDiscoveryFeature, UUID> {

    /**
     * Lists the features of one profile version.
     *
     * @param listingDiscoveryProfileId the profile version
     * @return possibly empty list
     */
    List<ListingDiscoveryFeature> findByListingDiscoveryProfileId(UUID listingDiscoveryProfileId);

    /**
     * Finds one feature within one profile version.
     *
     * @param listingDiscoveryProfileId the profile version
     * @param featureKey the feature
     * @return the feature, when the profile carries it
     */
    Optional<ListingDiscoveryFeature> findByListingDiscoveryProfileIdAndFeatureKey(
            UUID listingDiscoveryProfileId, String featureKey);

    /**
     * Loads one feature across the current profiles of a candidate set.
     *
     * <pre>{@code
     * SELECT f.* FROM listing_discovery_features f
     * JOIN listing_discovery_profiles p ON p.id = f.listing_discovery_profile_id
     * WHERE p.listing_id = ANY (:listingIds) AND p.status = 'CURRENT' AND f.feature_key = :featureKey
     * }</pre>
     *
     * @param listingIds the candidate listings
     * @param featureKey the feature
     * @return possibly empty list; candidates without the feature are simply absent
     */
    @Query("""
            SELECT f.* FROM listing_discovery_features f
            JOIN listing_discovery_profiles p ON p.id = f.listing_discovery_profile_id
            WHERE p.listing_id = ANY (:listingIds) AND p.status = 'CURRENT' AND f.feature_key = :featureKey
            """)
    List<ListingDiscoveryFeature> findCurrentFeature(@Param("listingIds") UUID[] listingIds,
            @Param("featureKey") String featureKey);
}
