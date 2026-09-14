package dev.ngb.backend.support.internal.model.remedy;

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
import dev.ngb.backend.platform.PolicyVersionStatus;

import dev.ngb.backend.support.internal.model.ApprovalTier;

/**
 * One allowlisted thing support may give, and on whose money.
 *
 * <p>Every remedy line names an entry here, and the entry fixes the permitted funders, the ceiling
 * formula, the downstream owner and the approval tier. An arbitrary amount-and-reason form is how a
 * support desk becomes an unbudgeted payments system.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("remedy_catalog_versions")
public class RemedyCatalogVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the catalog. */
    private String catalogKey;
    /** Which version of the catalog applies. */
    private int catalogVersion;
    /** Remedy code. */
    private String remedyCode;
    /** The market this row belongs to. */
    private @Nullable UUID marketId;
    /** Where the status stands. */
    private PolicyVersionStatus status;
    /** Effective from. */
    private @Nullable Instant effectiveFrom;
    /** Effective until. */
    private @Nullable Instant effectiveUntil;
    /** Which remedy kind this row carries. */
    private RemedyKind remedyKind;
    /** Decision basis allowed. */
    private String[] decisionBasisAllowed;
    /** Eligible case types. */
    private String[] eligibleCaseTypes;
    /** Eligible actor roles. */
    private String[] eligibleActorRoles;
    /** Who may fund this remedy, enforced when a line is written. */
    private String[] permittedFunders;
    /** Which currencies it may be paid in. */
    private String[] permittedCurrencies;
    /** Findings that must exist before this remedy may be granted. */
    private String[] requiredFindingQuestions;
    /** Evidence that must be present. */
    private String[] requiredEvidenceKinds;
    /** Whether monetary. */
    private boolean monetary;
    /** How the amount is computed; a monetary entry always has one. */
    private @Nullable String amountFormula;
    /** Maximum amount, in integer minor units of its currency. */
    private @Nullable Long maximumAmountMinor;
    /** ISO 4217 alphabetic code the maximum amount are denominated in. */
    private @Nullable String maximumAmountCurrency;
    /** Which ceiling scope this row carries. */
    private @Nullable CeilingScope ceilingScope;
    /** Target domain. */
    private @Nullable InstructionTargetDomain targetDomain;
    /** The command the owning domain exposes for it. */
    private @Nullable String targetCommandType;
    /** Tax treatment. */
    private RemedyTaxTreatment taxTreatment;
    /** Document requirement. */
    private DocumentRequirement documentRequirement;
    /** Authority tier. */
    private RemedyAuthorityTier authorityTier;
    /** Approval tier. */
    private ApprovalTier approvalTier;
    /** Whether the effect can be undone, and by whom. */
    private RemedyReversibility reversibility;
    /** How long, in days, the expiry runs. */
    private @Nullable Short expiryDays;
    /** Whether appeal available. */
    private boolean appealAvailable;
    /** Stable key naming the explanation template. */
    private String explanationTemplateKey;
    /** The approved by account holder this row belongs to. */
    private @Nullable UUID approvedByAccountHolderId;
    /** UTC instant approved. */
    private @Nullable Instant approvedAt;
    /** Digest of the content, so it can be shown later to be unchanged. */
    private String contentHash;
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
