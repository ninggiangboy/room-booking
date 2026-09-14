package dev.ngb.backend.review.internal.model.record_;

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
import dev.ngb.backend.platform.EvidenceScanState;
import dev.ngb.backend.platform.RetentionClass;

import dev.ngb.backend.platform.EvidenceScanState;
import dev.ngb.backend.platform.RetentionClass;
import dev.ngb.backend.review.internal.model.ReviewModerationState;
import dev.ngb.backend.review.internal.model.ReviewPublicProjection;


/**
 * A file a reviewer attached.
 *
 * <p>Behind the same quarantine discipline as every other upload: nothing may be shown publicly until
 * scanning has cleared it, which is a check constraint rather than a service step.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_media")
public class ReviewMedia {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Revision it was attached to. */
    private UUID reviewRevisionId;
    /** What kind of file. */
    private ReviewMediaType mediaType;
    /** Where the file lives. */
    private String objectReference;
    /** Hash of the file. */
    private String contentDigest;
    /** How large it is. */
    private @Nullable Long byteSize;
    /** Position in the gallery. */
    private short displayOrder;
    /** Whether malware scanning cleared it. */
    private EvidenceScanState scanState;
    /** What moderation says about it. */
    private ReviewModerationState moderationState;
    /** What the public can see of it. */
    private ReviewPublicProjection publicProjection;
    /** Processed rendering shown to the public. */
    private @Nullable String derivativeReference;
    /** How long it is kept. */
    private RetentionClass retentionClass;
    /** Whether deletion is barred. */
    private boolean legalHold;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;

    /**
     * Whether this file may be shown.
     *
     * @return true once scanning cleared it and moderation allows it
     */
    public boolean isShowable() {
        return scanState == EvidenceScanState.CLEAN
                && (moderationState == ReviewModerationState.PUBLISH
                        || moderationState == ReviewModerationState.RESTORED);
    }
}
