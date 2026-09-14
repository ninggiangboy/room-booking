package dev.ngb.backend.review.internal.repository.aggregate;

import dev.ngb.backend.review.internal.model.aggregate.ListingQualityProfile;
import dev.ngb.backend.review.types.DerivedProfileStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the ranking-quality projection for listings.
 *
 * <p>Never a substitute for the public average: this is a model output with an uncertainty interval,
 * and consumers of it are ranking, not display.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code listing_quality_profiles}.</p>
 */
public interface ListingQualityProfileRepository extends ListCrudRepository<ListingQualityProfile, UUID> {

    /**
     * Finds the profile in force for a listing.
     *
     * <p>Spring derives {@code WHERE listing_id = ? AND status = ?}, matching
     * {@code uk_listing_quality_profiles_current} when the status is {@code CURRENT}.</p>
     *
     * @param listingId listing
     * @param status status to match, normally {@code CURRENT}
     * @return the current profile, when one exists
     */
    Optional<ListingQualityProfile> findByListingIdAndStatus(UUID listingId,
            DerivedProfileStatus status);

    /**
     * Lists current profiles that are due for recomputation.
     *
     * <pre>{@code
     * SELECT *
     * FROM listing_quality_profiles
     * WHERE status = 'CURRENT'
     *   AND expires_at <= :at
     * ORDER BY expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Matches {@code idx_listing_quality_profiles_stale}. The instant is bound by the caller because
     * an index predicate may not read the clock.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum profiles to return
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT *
            FROM listing_quality_profiles
            WHERE status = 'CURRENT'
              AND expires_at <= :at
            ORDER BY expires_at
            LIMIT :batchSize
            """)
    List<ListingQualityProfile> findExpired(@Param("at") Instant at, @Param("batchSize") int batchSize);
}
