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
 * Append-only record of what somebody did with a review in public.
 *
 * <p>Kept narrow and bounded on purpose: click volume must never be able to harm review writes, and
 * this eventually belongs in analytical storage rather than beside the reviews themselves.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_interaction_events")
public class ReviewInteractionEvent {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Review interacted with. */
    private UUID reviewRecordId;
    /** Publication interval it was shown under. */
    private @Nullable UUID reviewPublicationId;
    /** What was done. */
    private ReviewInteractionType interactionType;
    /** Who did it, when they were signed in. */
    private @Nullable UUID actorAccountHolderId;
    /** Opaque session reference. */
    private @Nullable String sessionReference;
    /** Request identity, which makes the event idempotent. */
    private String requestId;
    /** Where in the product it happened. */
    private @Nullable String surface;
    /** Position in the list it was shown in. */
    private @Nullable Short position;
    /** When it happened. */
    private Instant occurredAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
