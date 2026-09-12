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
 * A correction, withdrawal or redaction recorded beside the message it changes.
 *
 * <p>Append-only by trigger. The original message is never edited, so what a participant relied on
 * last week is still reconstructable from the message and the revisions written after it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("message_revisions")
public class MessageRevision {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Message this revision applies to. */
    private UUID messageId;
    /** Position in this message's revision chain, starting at one. */
    private short revisionNumber;
    /** Why the revision was written. */
    private MessageRevisionKind revisionKind;
    /** Replacement content, when a correction stores it inline. */
    private @Nullable String replacementBodyText;
    /** Pointer to replacement content in encrypted storage. */
    private @Nullable String replacementBodyReference;
    /** SHA-256 of the replacement, required for a correction. */
    private @Nullable String replacementContentHash;
    /** What kind of actor wrote the revision. */
    private ActorType actorType;
    /** Who wrote it, when a person did. */
    private @Nullable UUID actorAccountHolderId;
    /** Why, in the vocabulary the appeal route uses. */
    private String reasonCode;
    /** Policy that required the revision, for moderation and redaction. */
    private @Nullable String policyReference;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
