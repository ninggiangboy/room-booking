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
 * One piece of provider evidence about a delivery attempt.
 *
 * <p>Append-only, and deduplicated on the provider's own event identity so replayed webhooks are
 * free. A late bounce or complaint after a delivered event is another row, not a rewrite: the
 * attempt's state is reduced from all of them.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("delivery_observations")
public class DeliveryObservation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Attempt this evidence is about. */
    private UUID deliveryAttemptId;
    /** Provider account that issued the event. */
    private UUID providerAccountId;
    /** The provider's event identity, unique within that account. */
    private String providerEventId;
    /** The provider's own event name. */
    private String eventType;
    /** How the evidence reached the platform. */
    private ObservationSource observationSource;
    /** When the provider says it happened. */
    private @Nullable Instant occurredAt;
    /** When the platform received it. */
    private Instant receivedAt;
    /** Whether the callback signature was verified. */
    private boolean signatureVerified;
    /** Pointer to the stored raw payload; required for webhooks. */
    private @Nullable String rawArtifactReference;
    /** SHA-256 of that payload. */
    private @Nullable String payloadHash;
    /** What this observation says the outcome is. */
    private @Nullable DeliveryOutcome reducedState;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
