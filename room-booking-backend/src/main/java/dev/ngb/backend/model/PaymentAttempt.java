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
 * One guest journey toward satisfying an obligation: this method, this provider, this sequence of
 * customer actions.
 *
 * <p>Reloading a page is not a new attempt; a new instrument is. Creating one means new guest
 * intent, a known-terminal previous attempt, or an approved routing recovery.</p>
 *
 * <p>{@link #state} is a projection for orchestration and the interface. The operations are the
 * auditable record of what actually succeeded.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payment_attempts")
public class PaymentAttempt {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Obligation being satisfied. */
    private UUID obligationId;
    /** Due component being satisfied, when the schedule has several. */
    private @Nullable UUID scheduleItemId;
    /** Position in this obligation's attempt history. */
    private short attemptNumber;
    /** Merchant account the attempt runs through. */
    private UUID providerAccountId;
    /** Route configuration selected for it. */
    private @Nullable UUID paymentRouteId;
    /** Stored instrument used, when the guest reused one. */
    private @Nullable UUID methodReferenceId;
    /** Instrument family. */
    private PaymentMethodFamily methodFamily;
    /** ISO 4217 code, copied from the obligation. */
    private String currency;
    /** Minor units this attempt asks for, copied from internal authority. */
    private long requestedAmountMinor;
    /** Minor units reserved at the provider so far. */
    private long authorizedAmountMinor;
    /** Minor units collected so far. */
    private long capturedAmountMinor;
    /** Progress of the journey. */
    private PaymentAttemptState state;
    /** Normalised reason it ended without collecting. */
    private @Nullable PaymentFailureCategory failureCategory;
    /**
     * Safe code behind what the guest is told.
     *
     * <p>Deliberately separate from {@link #restrictedFailureCode}. Issuer detail, fraud-rule
     * reasoning, and internal routing must never reach a browser.</p>
     */
    private @Nullable String guestReasonCode;
    /** Provider code, for operations and support only. */
    private @Nullable String restrictedFailureCode;
    /** Step the guest must complete away from the server request. */
    private @Nullable CustomerActionType actionType;
    /**
     * UTC instant the customer action stops being accepted.
     *
     * <p>Required whenever the attempt is waiting on the guest. An attempt with no deadline holds
     * inventory nobody will ever reclaim.</p>
     */
    private @Nullable Instant actionDeadlineAt;
    /** Version of the routing policy that chose the provider. */
    private @Nullable String routingPolicyVersion;
    /** Risk decision permitting the attempt. */
    private @Nullable UUID riskDecisionId;
    /** Version of the risk policy that produced it. */
    private @Nullable String riskPolicyVersion;
    /** Command idempotency record that created the attempt. */
    private @Nullable UUID idempotencyRecordId;
    /** Hex SHA-256 of the canonical request, so a replay can be told from a new intent. */
    private String requestDigest;
    /** Identifier following the whole booking saga. */
    private String correlationId;
    /** Kind of actor that started the attempt. */
    private BookingActorType actorType;
    /** Identity of that actor, when it has one. */
    private @Nullable UUID actorId;
    /** Surface the attempt was started from. */
    private String sourceChannel;
    /** UTC instant the attempt reached a terminal state. */
    private @Nullable Instant terminalAt;
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
     * Whether this attempt has finished and a new one may be started.
     *
     * @return {@code true} once no further outcome can change it
     */
    public boolean isTerminal() {
        return switch (state) {
            case CAPTURED, REFUNDED, VOIDED, AUTHORIZATION_EXPIRED, FAILED, CANCELLED, EXPIRED
                    -> true;
            case CREATED, SUBMITTING, REQUIRES_ACTION, PROCESSING, AUTHORIZED,
                    PARTIALLY_CAPTURED, PARTIALLY_REFUNDED -> false;
        };
    }

    /**
     * Whether the attempt is waiting on the guest at the given instant.
     *
     * @param decisionInstant the caller's single decision instant
     * @return {@code true} while a customer action is outstanding and its deadline has not passed
     */
    public boolean isAwaitingGuestAt(Instant decisionInstant) {
        return state == PaymentAttemptState.REQUIRES_ACTION
                && actionDeadlineAt != null
                && actionDeadlineAt.isAfter(decisionInstant);
    }
}
