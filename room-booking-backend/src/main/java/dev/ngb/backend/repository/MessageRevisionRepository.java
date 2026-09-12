package dev.ngb.backend.repository;

import dev.ngb.backend.model.MessageRevision;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the corrections and withdrawals written against messages.
 *
 * <p>The table is append-only by trigger, so these rows are evidence rather than state.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code message_revisions}.</p>
 */
public interface MessageRevisionRepository extends ListCrudRepository<MessageRevision, UUID> {

    /**
     * Returns a message's revision chain.
     *
     * <p>Spring derives {@code WHERE message_id = ? ORDER BY revision_number}.</p>
     *
     * @param messageId message whose revisions are wanted
     * @return possibly empty list, oldest revision first
     */
    List<MessageRevision> findAllByMessageIdOrderByRevisionNumber(UUID messageId);

    /**
     * Finds the newest revision of a message.
     *
     * <pre>{@code
     * SELECT *
     * FROM message_revisions
     * WHERE message_id = :messageId
     * ORDER BY revision_number DESC
     * LIMIT 1
     * }</pre>
     *
     * @param messageId message whose current projection is wanted
     * @return the latest revision, when the message has been revised
     */
    @Query("""
            SELECT *
            FROM message_revisions
            WHERE message_id = :messageId
            ORDER BY revision_number DESC
            LIMIT 1
            """)
    Optional<MessageRevision> findLatest(@Param("messageId") UUID messageId);
}
