package dev.ngb.backend.repository;

import dev.ngb.backend.model.ListingMedia;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the photographs and other media attached to listings.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code listing_media}.</p>
 */
public interface ListingMediaRepository extends ListCrudRepository<ListingMedia, UUID> {

    /**
     * Returns the media that may actually be shown for a listing, in gallery order.
     *
     * <pre>{@code
     * SELECT *
     * FROM listing_media
     * WHERE listing_id = :listingId
     *   AND scan_state = 'CLEAN'
     *   AND moderation_state = 'APPROVED'
     *   AND processing_state = 'READY'
     * ORDER BY display_order
     * }</pre>
     *
     * <p>All three gates are pushed into the query, matching {@code idx_listing_media_gallery}.
     * A caller that loaded everything and checked only moderation would serve an unscanned upload, or
     * an unprocessed original that still carries the camera's location metadata.</p>
     *
     * @param listingId listing whose gallery is wanted
     * @return possibly empty list of displayable media in order
     */
    @Query("""
            SELECT *
            FROM listing_media
            WHERE listing_id = :listingId
              AND scan_state = 'CLEAN'
              AND moderation_state = 'APPROVED'
              AND processing_state = 'READY'
            ORDER BY display_order
            """)
    List<ListingMedia> findDisplayableGallery(@Param("listingId") UUID listingId);

    /**
     * Finds a listing's cover image.
     *
     * <p>Spring derives {@code WHERE listing_id = ? AND is_cover = true}.
     * {@code uk_listing_media_one_cover} guarantees at most one row matches. The caller must still
     * check {@code isDisplayable}, since a cover can be chosen before processing finishes.</p>
     *
     * @param listingId listing whose cover is wanted
     * @return the cover media when one has been chosen
     */
    Optional<ListingMedia> findByListingIdAndIsCoverTrue(UUID listingId);

    /**
     * Finds every listing that has uploaded a byte-identical file.
     *
     * <p>Spring derives {@code WHERE content_digest = ?}. The same photograph appearing under two
     * different properties is a strong duplicate- or fake-listing signal, and is one of the cheaper
     * fraud checks available — a legitimate host re-using their own photo across their own listings
     * is the common benign case, which is why this returns rows rather than a verdict.</p>
     *
     * @param contentDigest SHA-256 digest of the file
     * @return possibly empty list of media sharing that content
     */
    List<ListingMedia> findAllByContentDigest(String contentDigest);
}
