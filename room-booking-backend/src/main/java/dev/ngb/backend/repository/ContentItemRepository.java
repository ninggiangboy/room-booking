package dev.ngb.backend.repository;

import dev.ngb.backend.model.ContentItem;
import dev.ngb.backend.model.ContentOwningDomain;
import dev.ngb.backend.model.ModeratedContentType;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the stable handles moderation decisions attach to.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code content_items}.</p>
 */
public interface ContentItemRepository extends ListCrudRepository<ContentItem, UUID> {

    /**
     * Finds the handle for one owning-domain resource.
     *
     * <pre>{@code
     * SELECT * FROM content_items
     * WHERE owning_domain = :owningDomain
     *   AND content_type = :contentType
     *   AND resource_type = :resourceType
     *   AND resource_id = :resourceId
     * }</pre>
     *
     * <p>Matches {@code uk_content_items_resource}, so at most one row can come back.</p>
     *
     * @param owningDomain domain holding the content
     * @param contentType kind of content
     * @param resourceType owning domain's resource type
     * @param resourceId owning domain's identifier
     * @return the handle, when one has been created
     */
    Optional<ContentItem> findByOwningDomainAndContentTypeAndResourceTypeAndResourceId(
            ContentOwningDomain owningDomain, ModeratedContentType contentType,
            String resourceType, UUID resourceId);

    /**
     * Reads one author's content.
     *
     * <pre>{@code
     * SELECT * FROM content_items WHERE author_subject_id = :authorSubjectId ORDER BY created_at DESC
     * }</pre>
     *
     * @param authorSubjectId the author
     * @return possibly empty list, most recent first
     */
    List<ContentItem> findByAuthorSubjectIdOrderByCreatedAtDesc(UUID authorSubjectId);
}
