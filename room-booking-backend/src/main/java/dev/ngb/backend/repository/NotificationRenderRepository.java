package dev.ngb.backend.repository;

import dev.ngb.backend.model.NotificationRender;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what the words came out as.
 *
 * <p>Append-only: a re-render is another row, which is what lets an audit reconstruct exactly what was
 * sent rather than what would be produced today.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code notification_renders}.</p>
 */
public interface NotificationRenderRepository extends ListCrudRepository<NotificationRender, UUID> {

    /**
     * Finds the successful render for an intent on a channel.
     *
     * <pre>{@code
     * SELECT *
     * FROM notification_renders
     * WHERE notification_intent_id = :intentId
     *   AND channel = :channel
     *   AND locale = :locale
     *   AND render_state = 'RENDERED'
     * }</pre>
     *
     * <p>Matches {@code uk_notification_renders_success}, so at most one row can come back.</p>
     *
     * @param intentId intent being dispatched
     * @param channel channel being sent on
     * @param locale locale rendered in
     * @return the render, when one succeeded
     */
    @Query("""
            SELECT *
            FROM notification_renders
            WHERE notification_intent_id = :intentId
              AND channel = :channel
              AND locale = :locale
              AND render_state = 'RENDERED'
            """)
    Optional<NotificationRender> findSuccessful(@Param("intentId") UUID intentId,
                                                @Param("channel") String channel,
                                                @Param("locale") String locale);

    /**
     * Returns renders that could not be produced.
     *
     * <pre>{@code
     * SELECT *
     * FROM notification_renders
     * WHERE render_state <> 'RENDERED'
     * ORDER BY created_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>This is the operations queue a missing required variable fails into.</p>
     *
     * @param batchSize maximum rows to return
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT *
            FROM notification_renders
            WHERE render_state <> 'RENDERED'
            ORDER BY created_at
            LIMIT :batchSize
            """)
    List<NotificationRender> findBlocked(@Param("batchSize") int batchSize);

    /**
     * Returns every render attempt for an intent.
     *
     * <p>Spring derives {@code WHERE notification_intent_id = ?}.</p>
     *
     * @param notificationIntentId intent being inspected
     * @return possibly empty list
     */
    List<NotificationRender> findAllByNotificationIntentId(UUID notificationIntentId);
}
