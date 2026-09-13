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
 * One exact version of a content item, as submitted.
 *
 * <p>Moderation happens against a revision, never against "the listing". An edit is a new revision,
 * which is the only thing that stops a clean text approval from publishing a changed link later.
 * Append-only, numbered, and a revision carrying an unscanned attachment may not default to
 * visible.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("content_revisions")
public class ContentRevision {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The item this is a version of. */
    private UUID contentItemId;
    /** Which version; unique within the item. */
    private int revisionNumber;
    /** Language it was written in. */
    private @Nullable String languageTag;
    /** Where the text is held; never the text itself. */
    private @Nullable String bodyReference;
    /** Hex digest of the body, so a decision can be tied to exact content. */
    private String bodyDigest;
    /** How many files came with it. */
    private short attachmentCount;
    /** What validation and scanning found; present exactly when there are attachments. */
    private @Nullable ContentAttachmentScanState attachmentScanState;
    /** Whether it carries links, which are checked separately. */
    private boolean containsLinks;
    /** The caller's key, so a retried submission collapses onto one revision. */
    private @Nullable String clientSubmissionId;
    /** Who submitted this version. */
    private @Nullable UUID authorSubjectId;
    /** How it is treated before a decision is taken. */
    private ContentVisibilityDefault visibilityDefault;
    /** When it was submitted. */
    private Instant submittedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
