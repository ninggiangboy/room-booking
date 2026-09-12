package dev.ngb.backend.repository;

import dev.ngb.backend.model.ConversationParticipant;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads conversation membership.
 *
 * <p>Authorization is evaluated at write and read time, so these are the reads that decide whether an
 * actor may see or send anything at all.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code conversation_participants}.</p>
 */
public interface ConversationParticipantRepository extends ListCrudRepository<ConversationParticipant, UUID> {

    /**
     * Finds an actor's current membership of a thread.
     *
     * <pre>{@code
     * SELECT *
     * FROM conversation_participants
     * WHERE conversation_id = :conversationId
     *   AND account_holder_id = :accountHolderId
     *   AND left_at IS NULL
     * }</pre>
     *
     * <p>An actor may hold two roles in the same thread only by holding two membership rows, which is why
     * this returns a list rather than an {@code Optional}.</p>
     *
     * @param conversationId thread being read
     * @param accountHolderId actor whose membership is wanted
     * @return possibly empty list of active memberships
     */
    @Query("""
            SELECT *
            FROM conversation_participants
            WHERE conversation_id = :conversationId
              AND account_holder_id = :accountHolderId
              AND left_at IS NULL
            """)
    List<ConversationParticipant> findActive(@Param("conversationId") UUID conversationId,
                                             @Param("accountHolderId") UUID accountHolderId);

    /**
     * Returns everybody currently in a thread.
     *
     * <p>Spring derives {@code WHERE conversation_id = ? AND left_at IS NULL}.</p>
     *
     * @param conversationId thread being read
     * @return possibly empty list of active memberships
     */
    List<ConversationParticipant> findAllByConversationIdAndLeftAtIsNull(UUID conversationId);

    /**
     * Returns elevated memberships whose access has lapsed.
     *
     * <pre>{@code
     * SELECT *
     * FROM conversation_participants
     * WHERE left_at IS NULL
     *   AND elevation_expires_at IS NOT NULL
     *   AND elevation_expires_at <= :at
     * ORDER BY elevation_expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>The instant is bound by the caller: index predicates may not read the clock.</p>
     *
     * @param at instant to evaluate expiry against
     * @param batchSize maximum rows to return
     * @return possibly empty list, longest lapsed first
     */
    @Query("""
            SELECT *
            FROM conversation_participants
            WHERE left_at IS NULL
              AND elevation_expires_at IS NOT NULL
              AND elevation_expires_at <= :at
            ORDER BY elevation_expires_at
            LIMIT :batchSize
            """)
    List<ConversationParticipant> findLapsedElevations(@Param("at") Instant at,
                                                       @Param("batchSize") int batchSize);
}
