package dev.ngb.backend.repository;

import dev.ngb.backend.model.PromotionVersion;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the frozen terms of campaigns.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code promotion_versions}.</p>
 *
 * <p>Redemptions cite a version rather than a campaign, so this is the table that decides what a
 * guest actually received and who agreed to fund it.</p>
 */
public interface PromotionVersionRepository extends ListCrudRepository<PromotionVersion, UUID> {

    /**
     * Returns the published terms applicable to a booking made at an instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM promotion_versions
     * WHERE promotion_id = :promotionId
     *   AND publication_state = 'PUBLISHED'
     *   AND (booking_window_from IS NULL OR booking_window_from <= :instant)
     *   AND (booking_window_until IS NULL OR booking_window_until > :instant)
     * ORDER BY version_number DESC
     * LIMIT 1
     * }</pre>
     *
     * <p>An open-ended window matches every instant, which is why the null checks are part of the
     * predicate rather than handled by a default value nobody chose.</p>
     *
     * @param promotionId campaign being applied
     * @param instant the booking's decision instant
     * @return the applicable terms, or empty when the campaign has none at that instant
     */
    @Query("""
            SELECT *
            FROM promotion_versions
            WHERE promotion_id = :promotionId
              AND publication_state = 'PUBLISHED'
              AND (booking_window_from IS NULL OR booking_window_from <= :instant)
              AND (booking_window_until IS NULL OR booking_window_until > :instant)
            ORDER BY version_number DESC
            LIMIT 1
            """)
    Optional<PromotionVersion> findApplicable(
            @Param("promotionId") UUID promotionId,
            @Param("instant") Instant instant);

    /**
     * Returns every version of a campaign, newest first.
     *
     * <p>Spring derives {@code WHERE promotion_id = ? ORDER BY version_number DESC}.</p>
     *
     * @param promotionId campaign whose history is wanted
     * @return possibly empty list, highest version number first
     */
    List<PromotionVersion> findAllByPromotionIdOrderByVersionNumberDesc(UUID promotionId);
}
