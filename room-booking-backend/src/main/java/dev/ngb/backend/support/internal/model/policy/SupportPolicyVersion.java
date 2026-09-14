package dev.ngb.backend.support.internal.model.policy;

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

import dev.ngb.backend.platform.PolicyVersionStatus;


/**
 * The effective-dated package a support decision is taken under.
 *
 * <p>A decision stores the version it selected, so reopening a case under newer rules produces a
 * superseding decision rather than silently recomputing history with today's terms.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("support_policy_versions")
public class SupportPolicyVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the policy. */
    private String policyKey;
    /** Which version of the policy applies. */
    private int policyVersion;
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
    /** Governed case types. */
    private String[] governedCaseTypes;
    /** Which instant selects the applicable version when a booking spans a change. */
    private OccurrenceTimeBasis occurrenceTimeBasis;
    /** The order entitlement is evaluated in, so a decision can prove which rung it stopped on. */
    private String[] precedenceLadder;
    /** Which version of the decision schema applies. */
    private int decisionSchemaVersion;
    /** Reference to the rule set, held in its owning system rather than copied here. */
    private String ruleSetReference;
    /** Stable key naming the remedy catalog. */
    private String remedyCatalogKey;
    /** Stable key naming the authority policy. */
    private String authorityPolicyKey;
    /** Reference to the business calendar, held in its owning system rather than copied here. */
    private String businessCalendarReference;
    /** Whether appeal available. */
    private boolean appealAvailable;
    /** How long, in days, the appeal window runs. */
    private @Nullable Short appealWindowDays;
    /** Whether appeal independence required. */
    private boolean appealIndependenceRequired;
    /** External complaint route. */
    private @Nullable String externalComplaintRoute;
    /** Which version of the disclosure applies. */
    private String disclosureVersion;
    /** Where the validation stands. */
    private PolicyValidationState validationState;
    /** The vectors the rule set was validated against. */
    private @Nullable String testVectorReference;
    /** The approved by account holder this row belongs to. */
    private @Nullable UUID approvedByAccountHolderId;
    /** UTC instant approved. */
    private @Nullable Instant approvedAt;
    /** Digest of the package, so a stored selection can be proven unchanged. */
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
