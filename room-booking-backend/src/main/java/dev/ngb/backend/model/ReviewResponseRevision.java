package dev.ngb.backend.model;

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
import org.springframework.data.relational.core.mapping.Table;

/**
 * The text of one version of a host response.
 *
 * <p>Insert-only, so a moderation decision stays tied to the words it judged.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_response_revisions")
public class ReviewResponseRevision {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Response this belongs to. */
    private UUID reviewResponseId;
    /** Position in the response's history. */
    private int revisionNumber;
    /** The words. */
    private String responseText;
    /** Locale they were written in. */
    private @Nullable String originalLocale;
    /** Hash of those words. */
    private String contentDigest;
    /** Host the response is from. */
    private UUID authorAccountHolderId;
    /** Who physically wrote it. */
    private @Nullable UUID actingAccountHolderId;
    /** Revision this replaces. */
    private @Nullable UUID supersedesRevisionId;
    /** When the platform received it. */
    private Instant receivedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
