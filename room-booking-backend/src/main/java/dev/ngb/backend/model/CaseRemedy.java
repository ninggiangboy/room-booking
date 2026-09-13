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
 * What a decision authorized, tracked separately from what actually happened.
 *
 * <p>{@code APPROVED} means an entitlement was authorized; only the downstream domain's own success
 * proves the money moved, and a posted ledger entry proves an accounting effect rather than a
 * beneficiary's bank receipt.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_remedies")
public class CaseRemedy {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** The case decision this row belongs to. */
    private UUID caseDecisionId;
    /** The damage claim this row belongs to. */
    private @Nullable UUID damageClaimId;
    /** Remedy code. */
    private String remedyCode;
    /** The remedy catalog version this row belongs to. */
    private UUID remedyCatalogVersionId;
    /** Which remedy kind this row carries. */
    private RemedyKind remedyKind;
    /** Whether this remedy moves money, which fixes whether it carries an amount at all. */
    private boolean monetary;
    /** Decision basis. */
    private RemedyDecisionBasis decisionBasis;
    /** ISO 4217 alphabetic code the amounts on this row are denominated in. */
    private @Nullable String currency;
    /** Total amount, in integer minor units of its currency. */
    private @Nullable Long totalAmountMinor;
    /** How much actually moved downstream, which is not what was authorized. */
    private long executedAmountMinor;
    /** Where the state stands. */
    private CaseRemedyState state;
    /** Whether approval required. */
    private boolean approvalRequired;
    /** The exact terms that were approved; the row is frozen against them afterwards. */
    private @Nullable String approvalDigest;
    /** UTC instant approved. */
    private @Nullable Instant approvedAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String rejectionReason;
    /** UTC instant expires. */
    private @Nullable Instant expiresAt;
    /** UTC instant execution started. */
    private @Nullable Instant executionStartedAt;
    /** UTC instant execution completed. */
    private @Nullable Instant executionCompletedAt;
    /** UTC instant the outcome became unknown. */
    private @Nullable Instant unknownSince;
    /** UTC instant the unknown outcome must be chased again. */
    private @Nullable Instant nextReconciliationAt;
    /** Who owns an outcome nobody could resolve. */
    private @Nullable UUID exceptionOwnerAccountHolderId;
    /** UTC instant exception deadline. */
    private @Nullable Instant exceptionDeadlineAt;
    /** The remedy this one reverses, which only a new decision may authorize. */
    private @Nullable UUID reversesRemedyId;
    /** The reversal decision this row belongs to. */
    private @Nullable UUID reversalDecisionId;
    /** Stable key naming the explanation template. */
    private String explanationTemplateKey;
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
