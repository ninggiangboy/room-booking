package dev.ngb.backend.repository;

import dev.ngb.backend.model.NotificationPreference;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what recipients chose about optional notices.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code notification_preferences}.</p>
 */
public interface NotificationPreferenceRepository extends ListCrudRepository<NotificationPreference, UUID> {

    /**
     * Finds the preference that applies to a purpose on a channel.
     *
     * <pre>{@code
     * SELECT *
     * FROM notification_preferences
     * WHERE account_holder_id = :accountHolderId
     *   AND category_code = :categoryCode
     *   AND channel = :channel
     *   AND (purpose_code = :purposeCode OR purpose_code IS NULL)
     * ORDER BY purpose_code NULLS LAST
     * LIMIT 1
     * }</pre>
     *
     * <p>A purpose-specific override wins over the category default, which is what the ordering is for.</p>
     *
     * @param accountHolderId recipient
     * @param categoryCode category being evaluated
     * @param purposeCode purpose being evaluated
     * @param channel channel being considered
     * @return the governing preference, when one is set
     */
    @Query("""
            SELECT *
            FROM notification_preferences
            WHERE account_holder_id = :accountHolderId
              AND category_code = :categoryCode
              AND channel = :channel
              AND (purpose_code = :purposeCode OR purpose_code IS NULL)
            ORDER BY purpose_code NULLS LAST
            LIMIT 1
            """)
    Optional<NotificationPreference> findGoverning(@Param("accountHolderId") UUID accountHolderId,
                                                   @Param("categoryCode") String categoryCode,
                                                   @Param("purposeCode") String purposeCode,
                                                   @Param("channel") String channel);

    /**
     * Returns everything a recipient has configured.
     *
     * <p>Spring derives {@code WHERE account_holder_id = ?}.</p>
     *
     * @param accountHolderId recipient whose settings are wanted
     * @return possibly empty list
     */
    List<NotificationPreference> findAllByAccountHolderId(UUID accountHolderId);
}
