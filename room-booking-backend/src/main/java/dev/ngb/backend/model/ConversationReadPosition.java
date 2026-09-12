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
 * How far one participant has read a thread.
 *
 * <p>The highest contiguous visible sequence, guarded monotonic by trigger: a device syncing late
 * cannot lower it and make a read thread look unread. It records presentation only -- not
 * comprehension, presence, contract acceptance or consent.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("conversation_read_positions")
public class ConversationReadPosition {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Thread being read. */
    private UUID conversationId;
    /** Membership row whose position this is. */
    private UUID participantId;
    /** Highest contiguous sequence shown to this participant. */
    private long lastReadSequence;
    /** When that position was reached. */
    private @Nullable Instant readAt;
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
     * How many messages remain unread.
     *
     * @param lastIssuedSequence highest sequence the conversation has issued
     * @return the unread count, never negative
     */
    public long unreadCount(long lastIssuedSequence) {
        return Math.max(0, lastIssuedSequence - lastReadSequence);
    }
}
