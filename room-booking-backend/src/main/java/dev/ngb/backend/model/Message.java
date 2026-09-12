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
 * One thing somebody said, or one fact the platform reported.
 *
 * <p>A message is evidence from the moment it exists: the words, the sender, the order and the
 * content hash are frozen by trigger. What may still move is presentation -- withdrawal, masking,
 * quarantine -- and the pointer to the latest revision. Corrections are {@link MessageRevision}
 * rows beside the original, so a dispute can be shown both.</p>
 *
 * <p>The body is stored either inline or behind a reference to encrypted storage, never both.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("messages")
public class Message {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Thread this message belongs to. */
    private UUID conversationId;
    /** Position in the thread, issued by the conversation's allocator. */
    private long sequenceNumber;
    /** Membership row that entitled the sender; absent for platform messages. */
    private @Nullable UUID senderParticipantId;
    /** Who sent it; absent for platform messages. */
    private @Nullable UUID senderAccountHolderId;
    /** The envelope this message carries. */
    private MessageType messageType;
    /** Author content, when it is stored inline. */
    private @Nullable String bodyText;
    /** Pointer to the content in encrypted storage, when it is stored there. */
    private @Nullable String bodyReference;
    /** SHA-256 of the retained original bytes, so a later claim about it is checkable. */
    private String contentHash;
    /** Language the author said they wrote in. */
    private @Nullable String declaredLanguage;
    /** Language detection thought they wrote in. */
    private @Nullable String detectedLanguage;
    /** Message being replied to, when the client threaded it. */
    private @Nullable UUID replyToMessageId;
    /** Client send key; a retry with the same key returns the original message. */
    private @Nullable String idempotencyKey;
    /** Committed domain event a platform message reports. */
    private @Nullable UUID sourceEventId;
    /** Type of that event, such as {@code booking.confirmed.v1}. */
    private @Nullable String sourceEventType;
    /** Server-issued action a structured action offers. */
    private @Nullable String actionType;
    /** Resource that action acts on; never an amount or a transition. */
    private @Nullable UUID actionResourceId;
    /** When the offered action stops being valid. */
    private @Nullable Instant actionExpiresAt;
    /** How the message is presented now. */
    private MessageVisibility visibilityState;
    /** Highest revision written against this message; zero while unedited. */
    private short latestRevisionNumber;
    /** When the message was withdrawn. */
    private @Nullable Instant withdrawnAt;
    /** How the content must be protected. */
    private SensitivityClass sensitivityClass;
    /** How long it is kept before retention may remove it. */
    private RetentionClass retentionClass;
    /** Whether a hold blocks deletion regardless of retention. */
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
     * Whether the platform wrote this message rather than a person.
     *
     * @return {@code true} for system facts, instruction updates and incident updates
     */
    public boolean isSystemGenerated() {
        return senderAccountHolderId == null;
    }
    /**
     * Whether the message has been corrected or withdrawn since it was sent.
     *
     * @return {@code true} once at least one revision exists
     */
    public boolean isEdited() {
        return latestRevisionNumber > 0;
    }
    /**
     * Whether ordinary participants see the original content.
     *
     * @return {@code true} only while the message is visible
     */
    public boolean isVisible() {
        return visibilityState == MessageVisibility.VISIBLE;
    }
}
