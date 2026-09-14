package dev.ngb.backend.discovery.internal.repository.profile;

import dev.ngb.backend.discovery.internal.model.profile.ListingOutcomeAggregate;
import dev.ngb.backend.review.types.DerivedProfileStatus;
import dev.ngb.backend.discovery.internal.model.profile.OutcomeWindowKind;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.discovery.internal.model.profile.ListingOutcomeAggregate;
import dev.ngb.backend.discovery.internal.model.profile.OutcomeWindowKind;
import dev.ngb.backend.review.types.DerivedProfileStatus;


/**
 * Reads funnel counts for a listing beside the exposure conditions that produced them.
 *
 * <p>Callers computing a rate must use the propensity-weighted denominator where the aggregate offers
 * one. A raw click-through rate is a statement about past ranking, not about the listing.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code listing_outcome_aggregates}.</p>
 */
public interface ListingOutcomeAggregateRepository extends ListCrudRepository<ListingOutcomeAggregate, UUID> {

    /**
     * Finds the current aggregate for one listing and window.
     *
     * @param listingId the listing
     * @param windowKind the window
     * @param status normally {@code CURRENT}
     * @return the aggregate, when one has been computed
     */
    Optional<ListingOutcomeAggregate> findByListingIdAndWindowKindAndStatus(UUID listingId,
            OutcomeWindowKind windowKind, DerivedProfileStatus status);

    /**
     * Loads current aggregates for a candidate set and one window.
     *
     * <pre>{@code
     * SELECT * FROM listing_outcome_aggregates
     * WHERE listing_id = ANY (:listingIds) AND window_kind = :windowKind AND status = 'CURRENT'
     * }</pre>
     *
     * @param listingIds the candidate listings
     * @param windowKind the window
     * @return possibly empty list
     */
    @Query("""
            SELECT * FROM listing_outcome_aggregates
            WHERE listing_id = ANY (:listingIds) AND window_kind = :windowKind AND status = 'CURRENT'
            """)
    List<ListingOutcomeAggregate> findCurrentForListings(
            @Param("listingIds") UUID[] listingIds, @Param("windowKind") String windowKind);

    /**
     * Lists aggregates not reconciled against authoritative bookings and reviews since an instant.
     *
     * <pre>{@code
     * SELECT * FROM listing_outcome_aggregates
     * WHERE status = 'CURRENT' AND (reconciled_at IS NULL OR reconciled_at < :since)
     * ORDER BY reconciled_at NULLS FIRST
     * LIMIT :limit
     * }</pre>
     *
     * @param since reconciliation horizon
     * @param limit batch size
     * @return possibly empty list, never-reconciled rows first
     */
    @Query("""
            SELECT * FROM listing_outcome_aggregates
            WHERE status = 'CURRENT' AND (reconciled_at IS NULL OR reconciled_at < :since)
            ORDER BY reconciled_at NULLS FIRST
            LIMIT :limit
            """)
    List<ListingOutcomeAggregate> findUnreconciled(@Param("since") Instant since,
            @Param("limit") int limit);
}
