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
 * The platform's side of a claim held by somebody else.
 *
 * <p>The provider key is stable and written before the first call, so a timeout is answered by querying
 * that key rather than creating a second claim to make a screen move. Provider acceptance is evidence
 * of a decision, not proof that money arrived.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("external_claims")
public class ExternalClaim {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** The damage claim this row belongs to. */
    private @Nullable UUID damageClaimId;
    /** The protection program version this row belongs to. */
    private UUID protectionProgramVersionId;
    /** The coverage snapshot this row belongs to. */
    private UUID coverageSnapshotId;
    /** The provider account this row belongs to. */
    private UUID providerAccountId;
    /** Stable key written before the first call; a retry queries it rather than making a second claim. */
    private String providerClaimKey;
    /** The provider’s own reference, once it gives one. */
    private @Nullable String providerClaimReference;
    /** Where the state stands. */
    private ExternalClaimState state;
    /** ISO 4217 alphabetic code the amounts on this row are denominated in. */
    private String currency;
    /** Requested amount, in integer minor units of its currency. */
    private long requestedAmountMinor;
    /** Approved amount, in integer minor units of its currency. */
    private @Nullable Long approvedAmountMinor;
    /** Paid amount, in integer minor units of its currency. */
    private long paidAmountMinor;
    /** The local approval decision this row belongs to. */
    private @Nullable UUID localApprovalDecisionId;
    /** UTC instant submission deadline. */
    private @Nullable Instant submissionDeadlineAt;
    /** UTC instant to ask the provider again about an unknown outcome. */
    private @Nullable Instant nextQueryAt;
    /** UTC instant last observed. */
    private @Nullable Instant lastObservedAt;
    /** Highest observation sequence applied; a lower one is a stale regression. */
    private long observationSequence;
    /** Denial reason code. */
    private @Nullable String denialReasonCode;
    /** Reference to the information request, held in its owning system rather than copied here. */
    private @Nullable String informationRequestReference;
    /** Reference to the appeal or complaint, held in its owning system rather than copied here. */
    private @Nullable String appealOrComplaintReference;
    /** The ledger entry that proves the money arrived, required to reconcile. */
    private @Nullable UUID ledgerTransactionId;
    /** UTC instant reconciled. */
    private @Nullable Instant reconciledAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;
}
