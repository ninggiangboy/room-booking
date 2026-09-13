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
 * What a role may do unaided, and where an independent approver becomes mandatory.
 *
 * <p>Authority is evaluated at execution time against this version, so revoking a grant stops the next
 * command even though the case stays assigned to the same person.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("authority_policy_versions")
public class AuthorityPolicyVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the authority policy. */
    private String authorityPolicyKey;
    /** Which version of the authority applies. */
    private int authorityVersion;
    /** Role code. */
    private String roleCode;
    /** The market this row belongs to. */
    private @Nullable UUID marketId;
    /** The legal entity this row belongs to. */
    private @Nullable UUID legalEntityId;
    /** Where the status stands. */
    private PolicyVersionStatus status;
    /** Effective from. */
    private @Nullable Instant effectiveFrom;
    /** Effective until. */
    private @Nullable Instant effectiveUntil;
    /** Permitted remedy kinds. */
    private String[] permittedRemedyKinds;
    /** Permitted case types. */
    private String[] permittedCaseTypes;
    /** Maximum severity. */
    private @Nullable CaseSeverity maximumSeverity;
    /** Permitted sensitivity. */
    private String[] permittedSensitivity;
    /** Maximum amount, in integer minor units of its currency. */
    private @Nullable Long maximumAmountMinor;
    /** ISO 4217 alphabetic code the maximum amount are denominated in. */
    private @Nullable String maximumAmountCurrency;
    /** Aggregate daily exposure this role may authorize, in minor units. */
    private @Nullable Long dailyExposureMinor;
    /** ISO 4217 alphabetic code the daily exposure are denominated in. */
    private @Nullable String dailyExposureCurrency;
    /** Approval tier above maximum. */
    private ApprovalTier approvalTierAboveMaximum;
    /** Whether independent approver required. */
    private boolean independentApproverRequired;
    /** Whether step up authentication required. */
    private boolean stepUpAuthenticationRequired;
    /** Which conflicts disqualify an actor from acting. */
    private String[] conflictRules;
    /** Required certifications. */
    private String[] requiredCertifications;
    /** Whether on call only. */
    private boolean onCallOnly;
    /** Whether break glass permitted. */
    private boolean breakGlassPermitted;
    /** Exactly which urgent actions break-glass covers. */
    private String[] breakGlassActions;
    /** How long a break-glass grant may live. */
    private @Nullable Short breakGlassMaximumMinutes;
    /** Whether break glass review required. */
    private boolean breakGlassReviewRequired;
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
