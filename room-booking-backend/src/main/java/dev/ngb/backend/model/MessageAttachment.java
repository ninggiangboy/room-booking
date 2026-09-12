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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One upload, from declaration through quarantine to the message that cites it.
 *
 * <p>The conversation is recorded before any message exists, because authorization happens when the
 * upload is declared. The database refuses to link an attachment to a message unless the scan
 * approved it, which is what makes the staged flow more than a convention.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("message_attachments")
public class MessageAttachment {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Thread the upload was authorized against. */
    private UUID conversationId;
    /** Message citing this object; set only once the scan approved it. */
    private @Nullable UUID messageId;
    /** Membership row that uploaded it. */
    private @Nullable UUID uploadedByParticipantId;
    /** Key of the object in storage. */
    private String objectKey;
    /** Bucket holding it; quarantine and approved storage differ. */
    private String storageBucket;
    /** What the object is for. */
    private AttachmentPurpose attachmentPurpose;
    /** Media type the client declared. */
    private String declaredMediaType;
    /** Media type verification actually found. */
    private @Nullable String verifiedMediaType;
    /** Size the client declared. */
    private long declaredSizeBytes;
    /** Size verification measured. */
    private @Nullable Long verifiedSizeBytes;
    /** Checksum the upload is verified against. */
    private String checksumSha256;
    /** How far quarantine has got. */
    private AttachmentScanState scanState;
    /** When verification finished. */
    private @Nullable Instant scanCompletedAt;
    /** Why it was rejected or quarantined. */
    private @Nullable String rejectionReason;
    /** Whether metadata stripping still has to happen. */
    private AttachmentTransformationState transformationState;
    /** Protected original kept where evidence retention requires it. */
    private @Nullable String originalObjectKey;
    /** How the object must be protected. */
    private SensitivityClass sensitivityClass;
    /** How long it is kept. */
    private RetentionClass retentionClass;
    /** Whether a hold blocks deletion. */
    private boolean legalHold;
    /** The hold that applies, required while held. */
    private @Nullable String holdReference;
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
     * Whether a message may cite this object.
     *
     * @return {@code true} once the scan approved it
     */
    public boolean isAttachable() {
        return scanState == AttachmentScanState.APPROVED;
    }
}
