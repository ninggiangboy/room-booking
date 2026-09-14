package dev.ngb.backend.trust.internal.model.content;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;
import dev.ngb.backend.market.internal.model.market.Market;

import dev.ngb.backend.market.internal.model.market.Market;


/**
 * A stable handle for something somebody wrote.
 *
 * <p>The text itself stays in the domain that owns it. What lives here is the identity moderation
 * decisions attach to, so this domain does not become a second copy of every message and listing on
 * the platform. Deleting content in its own domain does not erase the moderation history: where
 * lawful evidence must remain, the lifecycle says so rather than pretending the record is gone.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("content_items")
public class ContentItem {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Which domain holds the content itself. */
    private ContentOwningDomain owningDomain;
    /** What kind of content it is. */
    private ModeratedContentType contentType;
    /** The owning domain's resource type. */
    private String resourceType;
    /** The owning domain's identifier for it. */
    private UUID resourceId;
    /** Who wrote it. */
    private @Nullable UUID authorSubjectId;
    /** Who it was written for. */
    private ContentAudienceScope audienceScope;
    /** Market it belongs to. */
    private @Nullable UUID marketId;
    /** Whether it still exists in its own domain. */
    private ContentItemLifecycle lifecycle;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;
}
