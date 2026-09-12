package dev.ngb.backend.repository;

import dev.ngb.backend.model.ContactDeliveryHealth;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads whether destinations still accept delivery.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code contact_delivery_health}.</p>
 */
public interface ContactDeliveryHealthRepository extends ListCrudRepository<ContactDeliveryHealth, UUID> {

    /**
     * Finds the health record for a contact version on a channel.
     *
     * <p>Spring derives {@code WHERE contact_channel_id = ? AND contact_channel_version = ? AND
     * channel = ?}, matching {@code uk_contact_delivery_health_contact}.</p>
     *
     * @param contactChannelId identity-owned contact row
     * @param contactChannelVersion version of that row
     * @param channel channel being checked
     * @return the health record, when one exists
     */
    Optional<ContactDeliveryHealth> findByContactChannelIdAndContactChannelVersionAndChannel(
            UUID contactChannelId, long contactChannelVersion, String channel);

    /**
     * Returns suppressed destinations waiting for a human look.
     *
     * <pre>{@code
     * SELECT *
     * FROM contact_delivery_health
     * WHERE review_state = 'PENDING'
     * ORDER BY updated_at
     * LIMIT :batchSize
     * }</pre>
     *
     * @param batchSize maximum rows to return
     * @return possibly empty list, longest waiting first
     */
    @Query("""
            SELECT *
            FROM contact_delivery_health
            WHERE review_state = 'PENDING'
            ORDER BY updated_at
            LIMIT :batchSize
            """)
    List<ContactDeliveryHealth> findPendingReview(@Param("batchSize") int batchSize);
}
