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
import org.springframework.data.relational.core.mapping.Table;

/**
 * One verified thing a provider said, kept forever.
 *
 * <p>The current state of an operation is a reduction over these rows, so a stale webhook is
 * stored and marked ignored rather than dropped — otherwise the reduction is not reproducible and
 * a disagreement about money has no record to settle it.</p>
 *
 * <p>{@link #operationId} is nullable because an orphan provider object is evidence too, and
 * guessing which operation it belongs to is exactly what must not happen.</p>
 *
 * <p>Append-only: the table has no version and rejects updates and deletes by trigger.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payment_operation_observations")
public class PaymentOperationObservation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Operation this evidence is about; absent when it could not be mapped. */
    private @Nullable UUID operationId;
    /** Merchant account the evidence came from. */
    private UUID providerAccountId;
    /** How the evidence reached the platform. */
    private ObservationSource source;
    /** Provider event identifier, for webhook evidence. */
    private @Nullable String providerEventId;
    /** Provider identifier of the object described. */
    private @Nullable String providerObjectRef;
    /** The provider's own status string, unmodified. */
    private String providerStatus;
    /** That status mapped into the platform's vocabulary. */
    private NormalizedProviderState normalizedState;
    /** ISO 4217 code the provider reported. */
    private @Nullable String currency;
    /** Minor units the provider reported. */
    private @Nullable Long amountMinor;
    /** UTC instant the provider says it happened, when supplied. */
    private @Nullable Instant providerOccurredAt;
    /** UTC instant the platform received it. */
    private Instant receivedAt;
    /** Hex SHA-256 of the payload, so the same evidence is not stored twice. */
    private String payloadDigest;
    /** Pointer to a bounded restricted copy of the payload, where retention justifies one. */
    private @Nullable String payloadReference;
    /** How the evidence was proved authentic. */
    private VerificationMethod verificationMethod;
    /** Signing key version that verified it; required for webhook evidence. */
    private @Nullable String verificationKeyVersion;
    /** What the reducer did with it. */
    private ReducerOutcome reducerOutcome;
    /** Why it conflicts with internal facts, when it was quarantined. */
    private @Nullable String quarantineReason;

    /**
     * Whether this observation advanced the operation's state.
     *
     * @return {@code true} when the reducer applied it
     */
    public boolean wasApplied() {
        return reducerOutcome == ReducerOutcome.APPLIED;
    }

    /**
     * Whether this observation describes provider money with no internal owner.
     *
     * @return {@code true} for an orphan or quarantined observation
     */
    public boolean needsInvestigation() {
        return reducerOutcome == ReducerOutcome.UNMAPPED
                || reducerOutcome == ReducerOutcome.QUARANTINED;
    }
}
