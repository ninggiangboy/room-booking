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
 * The request this domain sends to whoever owns the effect, and where the answer lands.
 *
 * <p>Support never writes a refund, a ledger posting or a payout release itself. A lost response is
 * queried by the same idempotency key; it is never retried under a new one, because a second identity
 * downstream is how one refund becomes two.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("remedy_instructions")
public class RemedyInstruction {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The case remedy this row belongs to. */
    private UUID caseRemedyId;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** The case decision this row belongs to. */
    private UUID caseDecisionId;
    /** Which version of the instruction applies. */
    private int instructionVersion;
    /** Target domain. */
    private InstructionTargetDomain targetDomain;
    /** Which command type this row carries. */
    private String commandType;
    /** Globally unique command identity for this instruction. */
    private String commandId;
    /** The key the receiving domain deduplicates on; a retry reuses it. */
    private String idempotencyKey;
    /** Which target aggregate type this row carries. */
    private @Nullable String targetAggregateType;
    /** The target aggregate this row belongs to. */
    private @Nullable UUID targetAggregateId;
    /** The target aggregate version the instruction was built against. */
    private @Nullable Long expectedTargetVersion;
    /** Which beneficiary kind this row carries. */
    private BeneficiaryKind beneficiaryKind;
    /** The beneficiary account holder this row belongs to. */
    private @Nullable UUID beneficiaryAccountHolderId;
    /** ISO 4217 alphabetic code the amounts on this row are denominated in. */
    private @Nullable String currency;
    /** Amount, in integer minor units of its currency. */
    private @Nullable Long amountMinor;
    /** Digest of the funding split this instruction was authorized under. */
    private @Nullable String funderAllocationDigest;
    /** The source lines this instruction draws on, as the owning domain spells them. */
    private String[] sourceLineReferences;
    /** Reference to the tax document, held in its owning system rather than copied here. */
    private @Nullable String taxDocumentReference;
    /** Approved reason code recording why; free text never stands in for one. */
    private String reasonCode;
    /** Reference to the policy, held in its owning system rather than copied here. */
    private String policyReference;
    /** The approval the instruction rests on; money never leaves without one. */
    private @Nullable String approvalDigest;
    /** Stable key naming the requested explanation template. */
    private @Nullable String requestedExplanationTemplateKey;
    /** UTC instant deadline. */
    private @Nullable Instant deadlineAt;
    /** Where the state stands. */
    private RemedyInstructionState state;
    /** UTC instant dispatched. */
    private @Nullable Instant dispatchedAt;
    /** Where the result stands. */
    private @Nullable InstructionResultState resultState;
    /** The receiving domain’s own identifier for what it committed. */
    private @Nullable String resultReference;
    /** The result aggregate this row belongs to. */
    private @Nullable UUID resultAggregateId;
    /** What the receiving domain reports it actually moved. */
    private @Nullable Long resultAmountMinor;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String rejectionReason;
    /** How many attempt there are. */
    private int attemptCount;
    /** UTC instant to query an outcome that is still pending or unknown. */
    private @Nullable Instant nextQueryAt;
    /** UTC instant reconciled. */
    private @Nullable Instant reconciledAt;
    /** The correlation this row belongs to. */
    private @Nullable UUID correlationId;
    /** The causation this row belongs to. */
    private @Nullable UUID causationId;
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
