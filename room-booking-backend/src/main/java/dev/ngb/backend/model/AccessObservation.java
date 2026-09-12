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
 * Something a lock or its provider reported.
 *
 * <p>Append-only, with the provider-native references preserved. Adapter normalization may add
 * meaning but must never discard the timestamp, device identity or result code that an access failure
 * investigation runs on.</p>
 *
 * <p>A door opening is evidence that a door opened. It is not evidence of who opened it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("access_observations")
public class AccessObservation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Grant the event concerns. */
    private UUID accessGrantId;
    /** Operation that produced it, where there was one. */
    private @Nullable UUID accessOperationId;
    /** Provider account it came from. */
    private @Nullable UUID providerAccountId;
    /** Provider-native event identity, used for deduplication. */
    private @Nullable String providerEventId;
    /** Device the event came from. */
    private @Nullable String deviceReference;
    /** What was reported. */
    private AccessEventType eventType;
    /** Provider-native result code. */
    private @Nullable String resultCode;
    /** When the provider says it happened. */
    private Instant occurredAt;
    /** When the platform learned of it. */
    private Instant receivedAt;
    /** How the platform learned of it. */
    private ObservationSource source;
    /** How its authenticity was established. */
    private @Nullable VerificationMethod verificationMethod;
    /** Whether authenticity was actually established. */
    private boolean integrityVerified;
    /** Reference to the raw payload held under restricted retention. */
    private @Nullable String payloadReference;
    /** Hash of that payload. */
    private @Nullable String payloadHash;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
