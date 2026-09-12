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
 * One thread, scoped to the thing it is about.
 *
 * <p>The scope is the identity: at most one live conversation exists per scope, so an inquiry that
 * becomes a booking gets its own thread instead of widening pre-booking history to operators who
 * joined later. {@code nextSequence} is the allocator for message order -- the number the next
 * message will take -- and the database refuses to let it move backwards.</p>
 *
 * <p>Related rows load through their own repositories; Spring Data JDBC has no lazy loading.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("conversations")
public class Conversation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** What the thread is about. */
    private ConversationScope scopeType;
    /** The booking, listing, incident or case the scope names. */
    private UUID scopeReferenceId;
    /** Listing handle for authorization and routing, where the scope has one. */
    private @Nullable UUID listingId;
    /** Booking handle for authorization and routing, where the scope has one. */
    private @Nullable UUID bookingId;
    /** ISO 3166-1 alpha-2 market whose communication rules apply. */
    private @Nullable String marketCode;
    /** Whether the thread still accepts messages. */
    private ConversationStatus status;
    /** Why sending is limited, required while restricted. */
    private @Nullable String restrictionReason;
    /** When the thread stopped accepting messages. */
    private @Nullable Instant closedAt;
    /** Why it was closed or archived. */
    private @Nullable String closureReason;
    /** The sequence number the next message will take; never decreases. */
    private long nextSequence;
    /** When the most recent message arrived, for inbox ordering. */
    private @Nullable Instant lastMessageAt;
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
     * Whether a new message may be written into this thread.
     *
     * @return {@code true} while the conversation is open or merely restricted
     */
    public boolean acceptsMessages() {
        return status == ConversationStatus.OPEN || status == ConversationStatus.RESTRICTED;
    }
    /**
     * The highest sequence number already issued.
     *
     * @return the last issued sequence, or zero when no message has been sent
     */
    public long lastIssuedSequence() {
        return nextSequence - 1;
    }
}
