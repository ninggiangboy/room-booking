package dev.ngb.backend.repository;

import dev.ngb.backend.model.NotificationTemplate;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the approved wording used to render notices.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code notification_templates}.</p>
 */
public interface NotificationTemplateRepository extends ListCrudRepository<NotificationTemplate, UUID> {

    /**
     * Finds the active template for a family, channel and locale.
     *
     * <p>Spring derives {@code WHERE template_family = ? AND channel = ? AND locale = ? AND status = ?},
     * matching {@code uk_notification_templates_active} when the status is {@code ACTIVE}.</p>
     *
     * @param templateFamily family to render with
     * @param channel channel being rendered for
     * @param locale locale being rendered in
     * @param status lifecycle status wanted
     * @return the template, when one exists
     */
    Optional<NotificationTemplate> findByTemplateFamilyAndChannelAndLocaleAndStatus(
            String templateFamily, String channel, String locale, String status);

    /**
     * Returns every locale an active family can be rendered in on a channel.
     *
     * <pre>{@code
     * SELECT *
     * FROM notification_templates
     * WHERE template_family = :templateFamily
     *   AND channel = :channel
     *   AND status = 'ACTIVE'
     * ORDER BY locale
     * }</pre>
     *
     * <p>Used when the recipient's locale has no template and a documented fallback locale is chosen --
     * never by inventing a translation.</p>
     *
     * @param templateFamily family being rendered
     * @param channel channel being rendered for
     * @return possibly empty list, ordered by locale
     */
    @Query("""
            SELECT *
            FROM notification_templates
            WHERE template_family = :templateFamily
              AND channel = :channel
              AND status = 'ACTIVE'
            ORDER BY locale
            """)
    List<NotificationTemplate> findActiveLocales(@Param("templateFamily") String templateFamily,
                                                 @Param("channel") String channel);
}
