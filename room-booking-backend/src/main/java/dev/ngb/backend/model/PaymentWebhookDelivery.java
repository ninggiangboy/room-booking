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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One inbound provider event, recorded before any business effect is applied.
 *
 * <p>It exists so a provider can be acknowledged quickly and the work retried without asking for
 * the event again, and so a replay of an event already processed changes nothing. Deduplication
 * is by merchant account plus provider event identifier.</p>
 *
 * <p>Only a signature-verified delivery is ever processed into a provider fact.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payment_webhook_deliveries")
public class PaymentWebhookDelivery {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Merchant account the endpoint resolved to. */
    private UUID providerAccountId;
    /** Endpoint the delivery arrived on. */
    private String endpointKey;
    /** Provider event identifier. */
    private String providerEventId;
    /** Provider event type, as sent. */
    private String providerEventType;
    /** Provider object the event is about. */
    private @Nullable String providerObjectRef;
    /** Whether the signature over the exact received body verified. */
    private boolean signatureVerified;
    /** Signing key version that verified it. */
    private @Nullable String verificationKeyVersion;
    /** Hex SHA-256 of the received body. */
    private String payloadDigest;
    /** Pointer to a bounded restricted copy of the body, where retention justifies one. */
    private @Nullable String payloadReference;
    /** Progress of processing. */
    private WebhookDeliveryState state;
    /** Processing attempts made. */
    private int attemptCount;
    /** Worker currently processing it. */
    private @Nullable String leaseOwner;
    /** UTC instant that lease lapses. */
    private @Nullable Instant leaseExpiresAt;
    /** UTC instant it should be retried. */
    private @Nullable Instant nextAttemptAt;
    /** Why processing failed. */
    private @Nullable String failureClass;
    /** Evidence row this delivery produced. */
    private @Nullable UUID observationId;
    /** UTC instant it arrived. */
    private Instant receivedAt;
    /** UTC instant its effect was applied. */
    private @Nullable Instant processedAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;
}
