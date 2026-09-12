package dev.ngb.backend.repository;

import dev.ngb.backend.model.ConversationReadPosition;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads how far participants have read.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code conversation_read_positions}.</p>
 */
public interface ConversationReadPositionRepository extends ListCrudRepository<ConversationReadPosition, UUID> {

    /**
     * Finds a participant's read position.
     *
     * <p>Spring derives {@code WHERE participant_id = ?}, matching
     * {@code uk_conversation_read_positions_participant}.</p>
     *
     * @param participantId membership whose position is wanted
     * @return the position, when the participant has read anything
     */
    Optional<ConversationReadPosition> findByParticipantId(UUID participantId);

    /**
     * Returns every read position in a thread.
     *
     * <p>Spring derives {@code WHERE conversation_id = ?}.</p>
     *
     * @param conversationId thread being read
     * @return possibly empty list
     */
    List<ConversationReadPosition> findAllByConversationId(UUID conversationId);
}
