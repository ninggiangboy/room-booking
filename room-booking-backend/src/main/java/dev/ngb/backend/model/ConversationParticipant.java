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
 * One person's membership of one conversation, with the authority it rests on.
 *
 * <p>Membership is a row with a joined and a left instant rather than a derived fact, because "the
 * host" is a changing group of people: a co-host removed today must not read tomorrow's messages,
 * while the messages sent while they were present stay attributable to them. At most one active
 * membership exists per actor and role.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("conversation_participants")
public class ConversationParticipant {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Thread this membership belongs to. */
    private UUID conversationId;
    /** Who is participating; absent only for the platform itself. */
    private @Nullable UUID accountHolderId;
    /** What kind of actor they are. */
    private ActorType actorType;
    /** Why they are here. */
    private ConversationRole participantRole;
    /** Capabilities granted in this thread, such as READ, SEND, UPLOAD, VIEW_SENSITIVE. */
    private String[] permissions;
    /** What entitled them to join. */
    private ParticipantAuthoritySource authoritySource;
    /** The assignment, collaborator record or booking that carries that authority. */
    private @Nullable UUID authorityReferenceId;
    /** Which version of that authority was checked. */
    private @Nullable Integer authorityVersion;
    /** Why elevated access was granted; required for support and moderation. */
    private @Nullable String purposeCode;
    /** When elevated access ends; required for support and moderation. */
    private @Nullable Instant elevationExpiresAt;
    /** When membership began. */
    private Instant joinedAt;
    /** When membership ended; null while active. */
    private @Nullable Instant leftAt;
    /** Why membership ended, required once it has. */
    private @Nullable String removalReason;
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
     * Whether this membership is still current.
     *
     * @return {@code true} while the participant has not left
     */
    public boolean isActive() {
        return leftAt == null;
    }
    /**
     * Whether this membership still carries authority at the given instant.
     *
     * <p>Elevated access expires; ordinary membership does not.</p>
     *
     * @param at instant being evaluated
     * @return {@code true} when the participant is active and any elevation has not lapsed
     */
    public boolean isAuthorizedAt(Instant at) {
        return isActive() && (elevationExpiresAt == null || elevationExpiresAt.isAfter(at));
    }
}
