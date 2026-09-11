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
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Retry-safety record for one command, letting a repeated request be answered without re-running it.
 *
 * <p>The record stores digests and a bounded response projection, never the request body, a bearer
 * token, or a credential: it is a proof of outcome, not a response cache. {@code @Version} is
 * present because two concurrent replays race to settle the same row.</p>
 *
 * <p>This primitive does not replace business uniqueness. Two different idempotency keys can still
 * describe the same booking confirmation or refund, so the owning domain keeps its own unique
 * constraint on business identity.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("command_idempotency_records")
public class CommandIdempotencyRecord {

    /** Primary key of the record. */
    @Id
    private @Nullable UUID id;
    /** Kind of scope the key is unique within, such as {@code USER} or {@code ORGANIZATION}. */
    private String scopeType;
    /** Identifier of that scope, rendered as text so unrelated scopes share one column. */
    private String scopeKey;
    /** Command being made retry-safe, such as {@code booking.confirm}. */
    private String operation;
    /** SHA-256 digest of the caller-supplied idempotency key; the raw key is never stored. */
    private String keyDigest;
    /** SHA-256 digest of the canonical request, used to reject a reused key with different input. */
    private String requestDigest;
    /** API contract version the original request was interpreted under. */
    private String contractVersion;
    /** Kind of principal that issued the command, or {@code null} when not attributed. */
    private @Nullable ActorType actorType;
    /** Account that issued the command; paired with {@link #actorType}. */
    private @Nullable UUID actorId;
    /** Kind of resource the command owns, or {@code null} before it is known. */
    private @Nullable String resourceType;
    /** Identifier of that resource; paired with {@link #resourceType}. */
    private @Nullable UUID resourceId;
    /** Whether the command is still running or has settled. */
    private IdempotencyState state;
    /** HTTP status replayed to a repeated caller; present once the record has succeeded. */
    private @Nullable Short responseStatus;
    /** Kind of resource the successful response referred to. */
    private @Nullable String responseResourceType;
    /** Identifier of that resource; paired with {@link #responseResourceType}. */
    private @Nullable UUID responseResourceId;
    /** Human-facing reference, such as a booking confirmation code, safe to replay. */
    private @Nullable String responseReference;
    /** Bounded, non-sensitive projection of the response body replayed to a repeated caller. */
    private @Nullable JsonDocument responseProjection;
    /** Stable classification of the failure; present once the record has failed. */
    private @Nullable String failureCode;
    /** Instance holding the execution lease, or {@code null} when unclaimed. */
    private @Nullable String leaseOwner;
    /** UTC instant at which the lease lapses and the command becomes recoverable. */
    private @Nullable Instant leaseExpiresAt;
    /** Request identifier of the first attempt, kept for diagnosing a replay. */
    private String firstRequestId;
    /** Correlation identifier tying this command to its originating journey. */
    private String correlationId;
    /** UTC instant the record was created, supplied by the caller's decision instant. */
    private Instant createdAt;
    /** UTC instant the command settled; {@code null} while in progress. */
    private @Nullable Instant completedAt;
    /** UTC instant after which the record may be pruned. */
    private Instant retainUntil;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;

    /**
     * Reports whether this record can answer a replay without re-running the command.
     *
     * @return {@code true} when the record has settled, successfully or not
     */
    public boolean isSettled() {
        return state != IdempotencyState.IN_PROGRESS;
    }

    /**
     * Reports whether an abandoned execution may be recovered at the supplied instant.
     *
     * <p>A lapsed lease permits recovery. It never permits assuming that no effect occurred: the
     * command may have committed and crashed before settling its record.</p>
     *
     * @param instant the command's decision instant
     * @return {@code true} when the record is in progress and its lease has lapsed
     */
    public boolean isRecoverableAt(Instant instant) {
        return state == IdempotencyState.IN_PROGRESS
                && leaseExpiresAt != null
                && !leaseExpiresAt.isAfter(instant);
    }
}
