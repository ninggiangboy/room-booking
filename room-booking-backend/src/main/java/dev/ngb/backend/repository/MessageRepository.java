package dev.ngb.backend.repository;

import dev.ngb.backend.model.Message;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads messages.
 *
 * <p>Pagination is by sequence, never by timestamp: two messages can share an instant, and an opaque
 * cursor over sequence is what makes a page boundary stable.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code messages}.</p>
 */
public interface MessageRepository extends ListCrudRepository<Message, UUID> {

    /**
     * Reads a page of a thread by sequence.
     *
     * <pre>{@code
     * SELECT *
     * FROM messages
     * WHERE conversation_id = :conversationId
     *   AND sequence_number > :afterSequence
     * ORDER BY sequence_number
     * LIMIT :pageSize
     * }</pre>
     *
     * @param conversationId thread being read
     * @param afterSequence cursor; zero for the first page
     * @param pageSize maximum messages to return
     * @return possibly empty list in sequence order
     */
    @Query("""
            SELECT *
            FROM messages
            WHERE conversation_id = :conversationId
              AND sequence_number > :afterSequence
            ORDER BY sequence_number
            LIMIT :pageSize
            """)
    List<Message> findPage(@Param("conversationId") UUID conversationId,
                           @Param("afterSequence") long afterSequence,
                           @Param("pageSize") int pageSize);

    /**
     * Finds the message a replayed send already produced.
     *
     * <p>Spring derives {@code WHERE conversation_id = ? AND sender_account_holder_id = ? AND
     * idempotency_key = ?}, matching {@code uk_messages_sender_idempotency}. A retry returns the original
     * message instead of writing a second one.</p>
     *
     * @param conversationId thread being written to
     * @param senderAccountHolderId sender claiming the key
     * @param idempotencyKey the client's send key
     * @return the original message, when the send already happened
     */
    Optional<Message> findByConversationIdAndSenderAccountHolderIdAndIdempotencyKey(
            UUID conversationId, UUID senderAccountHolderId, String idempotencyKey);

    /**
     * Finds the message written for a committed domain event.
     *
     * <p>Spring derives {@code WHERE conversation_id = ? AND source_event_id = ?}. Replaying an event must
     * not post the same system fact twice.</p>
     *
     * @param conversationId thread being written to
     * @param sourceEventId committed event being reported
     * @return the system message, when one exists
     */
    Optional<Message> findByConversationIdAndSourceEventId(UUID conversationId, UUID sourceEventId);
}
