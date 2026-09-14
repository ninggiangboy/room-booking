package dev.ngb.backend.trust.internal.repository.content;

import dev.ngb.backend.trust.internal.model.content.ContentRevision;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.trust.internal.model.content.ContentRevision;


/**
 * Reads the exact versions moderation decides about.
 *
 * <p>Append-only. The latest-revision lookup matters because a decision that makes content visible is
 * refused unless it names the latest revision of its item.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code content_revisions}.</p>
 */
public interface ContentRevisionRepository extends ListCrudRepository<ContentRevision, UUID> {

    /**
     * Reads one item's revisions.
     *
     * <pre>{@code
     * SELECT * FROM content_revisions WHERE content_item_id = :contentItemId ORDER BY revision_number
     * }</pre>
     *
     * @param contentItemId the item
     * @return possibly empty list, earliest first
     */
    List<ContentRevision> findByContentItemIdOrderByRevisionNumber(UUID contentItemId);

    /**
     * Finds the latest revision of one item.
     *
     * <pre>{@code
     * SELECT * FROM content_revisions WHERE content_item_id = :contentItemId
     * ORDER BY revision_number DESC LIMIT 1
     * }</pre>
     *
     * <p>A moderation decision that publishes, masks or warns must name this revision; a trigger refuses
     * one naming an older version, because an old approval cannot publish a new revision.</p>
     *
     * @param contentItemId the item
     * @return the latest revision, when the item has one
     */
    @Query("""
            SELECT *
            FROM content_revisions
            WHERE content_item_id = :contentItemId
            ORDER BY revision_number DESC
            LIMIT 1
            """)
    Optional<ContentRevision> findLatest(@Param("contentItemId") UUID contentItemId);

    /**
     * Finds the revision a retried submission already produced.
     *
     * <pre>{@code
     * SELECT * FROM content_revisions
     * WHERE content_item_id = :contentItemId AND client_submission_id = :clientSubmissionId
     * }</pre>
     *
     * <p>Matches {@code uk_content_revisions_submission}, so a retry collapses onto one revision rather
     * than creating a second identical one a reviewer then has to decide about twice.</p>
     *
     * @param contentItemId the item
     * @param clientSubmissionId caller's submission key
     * @return the revision, when the key has been used
     */
    Optional<ContentRevision> findByContentItemIdAndClientSubmissionId(UUID contentItemId,
                                                                      String clientSubmissionId);
}
