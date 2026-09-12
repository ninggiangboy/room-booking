package dev.ngb.backend.repository;

import dev.ngb.backend.model.MessageModerationAction;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads moderation decisions about messages.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code message_moderation_actions}.</p>
 */
public interface MessageModerationActionRepository extends ListCrudRepository<MessageModerationAction, UUID> {

    /**
     * Returns what moderation did to a message, newest first.
     *
     * <p>Spring derives {@code WHERE message_id = ? ORDER BY decided_at DESC}.</p>
     *
     * @param messageId message being read
     * @return possibly empty list, newest decision first
     */
    List<MessageModerationAction> findAllByMessageIdOrderByDecidedAtDesc(UUID messageId);

    /**
     * Returns interventions with an appeal waiting.
     *
     * <pre>{@code
     * SELECT *
     * FROM message_moderation_actions
     * WHERE appeal_state = 'REQUESTED'
     * ORDER BY appealed_at
     * LIMIT :batchSize
     * }</pre>
     *
     * @param batchSize maximum appeals to return
     * @return possibly empty list, longest waiting first
     */
    @Query("""
            SELECT *
            FROM message_moderation_actions
            WHERE appeal_state = 'REQUESTED'
            ORDER BY appealed_at
            LIMIT :batchSize
            """)
    List<MessageModerationAction> findPendingAppeals(@Param("batchSize") int batchSize);
}
