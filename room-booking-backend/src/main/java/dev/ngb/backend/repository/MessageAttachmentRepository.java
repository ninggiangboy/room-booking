package dev.ngb.backend.repository;

import dev.ngb.backend.model.MessageAttachment;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads uploads and their quarantine state.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code message_attachments}.</p>
 */
public interface MessageAttachmentRepository extends ListCrudRepository<MessageAttachment, UUID> {

    /**
     * Returns the objects a message cites.
     *
     * <p>Spring derives {@code WHERE message_id = ? ORDER BY created_at}.</p>
     *
     * @param messageId message whose attachments are wanted
     * @return possibly empty list, oldest first
     */
    List<MessageAttachment> findAllByMessageIdOrderByCreatedAt(UUID messageId);

    /**
     * Returns uploads still waiting on verification.
     *
     * <pre>{@code
     * SELECT *
     * FROM message_attachments
     * WHERE scan_state IN ('PENDING', 'SCANNING')
     * ORDER BY created_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. {@code SKIP LOCKED} lets several scanner workers run without
     * queueing behind each other.</p>
     *
     * @param batchSize maximum uploads to claim
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT *
            FROM message_attachments
            WHERE scan_state IN ('PENDING', 'SCANNING')
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<MessageAttachment> claimPendingScans(@Param("batchSize") int batchSize);

    /**
     * Finds an upload by where it is stored.
     *
     * <p>Spring derives {@code WHERE storage_bucket = ? AND object_key = ?}, matching
     * {@code uk_message_attachments_object}. This is the read a scan callback arrives with.</p>
     *
     * @param storageBucket bucket holding the object
     * @param objectKey key within that bucket
     * @return the attachment, when one exists
     */
    Optional<MessageAttachment> findByStorageBucketAndObjectKey(String storageBucket, String objectKey);
}
