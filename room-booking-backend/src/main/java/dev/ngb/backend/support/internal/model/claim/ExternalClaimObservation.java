package dev.ngb.backend.support.internal.model.claim;

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
 * An append-only normalized provider statement.
 *
 * <p>Carries the sequence the claim was at when it arrived, so a late delivery that would move the claim
 * backwards is recorded and refused as a stale regression rather than quietly undoing a newer outcome.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("external_claim_observations")
public class ExternalClaimObservation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The external claim this row belongs to. */
    private UUID externalClaimId;
    /** The external claim submission this row belongs to. */
    private @Nullable UUID externalClaimSubmissionId;
    /** Where this statement sits in the provider’s order of events. */
    private long observationSequence;
    /** Which source kind this row carries. */
    private ExternalClaimObservationSource sourceKind;
    /** The provider’s event identity, so a redelivery converges. */
    private @Nullable String providerEventReference;
    /** Where the observed stands. */
    private ExternalClaimObservedState observedState;
    /** ISO 4217 alphabetic code the amounts on this row are denominated in. */
    private @Nullable String currency;
    /** Approved amount, in integer minor units of its currency. */
    private @Nullable Long approvedAmountMinor;
    /** Paid amount, in integer minor units of its currency. */
    private @Nullable Long paidAmountMinor;
    /** Denial reason code. */
    private @Nullable String denialReasonCode;
    /** Reference to the information request, held in its owning system rather than copied here. */
    private @Nullable String informationRequestReference;
    /** UTC instant provider deadline. */
    private @Nullable Instant providerDeadlineAt;
    /** Digest of the payload, so it can be shown later to be unchanged. */
    private String payloadDigest;
    /** UTC instant observed. */
    private Instant observedAt;
    /** UTC instant received. */
    private Instant receivedAt;
    /** Whether the observation moved the claim, or was kept as evidence only. */
    private boolean applied;
    /** Why it was not applied. */
    private @Nullable ObservationRejectionReason rejectionReason;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
