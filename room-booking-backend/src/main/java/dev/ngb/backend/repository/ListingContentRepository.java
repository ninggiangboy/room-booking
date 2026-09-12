package dev.ngb.backend.repository;

import dev.ngb.backend.model.ListingContent;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads a listing's wording in each language it exists in.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code listing_contents}.</p>
 */
public interface ListingContentRepository extends ListCrudRepository<ListingContent, UUID> {

    /**
     * Finds a listing's wording in one language.
     *
     * <p>Spring derives {@code WHERE listing_id = ? AND locale = ?}, matching
     * {@code uk_listing_contents_locale}. An empty result is a missing translation, which callers
     * surface by falling back to the source locale rather than showing an empty page.</p>
     *
     * @param listingId listing whose wording is wanted
     * @param locale BCP 47 locale requested
     * @return the wording when it exists in that language
     */
    Optional<ListingContent> findByListingIdAndLocale(UUID listingId, String locale);

    /**
     * Finds the wording the host actually wrote.
     *
     * <pre>{@code
     * SELECT * FROM listing_contents
     * WHERE listing_id = :listingId AND is_source = true
     * }</pre>
     *
     * <p>{@code uk_listing_contents_one_source} guarantees at most one row matches. This is the
     * authoritative wording: when a guest disputes what a listing promised, a machine translation is
     * not what the host said.</p>
     *
     * @param listingId listing whose source wording is wanted
     * @return the source wording when one exists
     */
    @Query("SELECT * FROM listing_contents WHERE listing_id = :listingId AND is_source = true")
    Optional<ListingContent> findSource(@Param("listingId") UUID listingId);

    /**
     * Returns content awaiting or flagged for moderation, oldest first.
     *
     * <pre>{@code
     * SELECT * FROM listing_contents
     * WHERE moderation_state IN ('PENDING', 'FLAGGED')
     * ORDER BY created_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Backs the moderator queue. Oldest first because unreviewed content is a host who cannot
     * publish.</p>
     *
     * @param batchSize maximum number of rows to return
     * @return possibly empty moderation queue
     */
    @Query("""
            SELECT * FROM listing_contents
            WHERE moderation_state IN ('PENDING', 'FLAGGED')
            ORDER BY created_at
            LIMIT :batchSize
            """)
    List<ListingContent> findModerationQueue(@Param("batchSize") int batchSize);
}
