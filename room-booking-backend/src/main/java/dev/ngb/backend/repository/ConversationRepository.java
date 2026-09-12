package dev.ngb.backend.repository;

import dev.ngb.backend.model.Conversation;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads conversation threads.
 *
 * <p>Sending a message allocates a sequence from the conversation row, so the send path takes the
 * lock through {@link #findByIdForUpdate(java.util.UUID)} inside its transaction.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code conversations}.</p>
 */
public interface ConversationRepository extends ListCrudRepository<Conversation, UUID> {

    /**
     * Finds the live thread for a scope.
     *
     * <pre>{@code
     * SELECT *
     * FROM conversations
     * WHERE scope_type = :scopeType
     *   AND scope_reference_id = :scopeReferenceId
     *   AND status <> 'ARCHIVED'
     * }</pre>
     *
     * <p>Matches {@code uk_conversations_active_scope}, so at most one row can come back.</p>
     *
     * @param scopeType what the thread is about
     * @param scopeReferenceId the booking, listing, incident or case
     * @return the live conversation, when one exists
     */
    @Query("""
            SELECT *
            FROM conversations
            WHERE scope_type = :scopeType
              AND scope_reference_id = :scopeReferenceId
              AND status <> 'ARCHIVED'
            """)
    Optional<Conversation> findLiveByScope(@Param("scopeType") String scopeType,
                                           @Param("scopeReferenceId") UUID scopeReferenceId);

    /**
     * Locks a conversation so a sequence can be allocated.
     *
     * <pre>{@code
     * SELECT * FROM conversations WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Requires an active transaction. The send path reads the row here, verifies membership, takes
     * {@code next_sequence}, writes the message, and raises the allocator in the same transaction; the
     * database refuses a message whose sequence the allocator never issued.</p>
     *
     * @param id conversation being written to
     * @return the locked conversation, when it exists
     */
    @Query("SELECT * FROM conversations WHERE id = :id FOR UPDATE")
    Optional<Conversation> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Returns the threads attached to a booking.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY created_at}.</p>
     *
     * @param bookingId booking whose threads are wanted
     * @return possibly empty list, oldest first
     */
    List<Conversation> findAllByBookingIdOrderByCreatedAt(UUID bookingId);
}
