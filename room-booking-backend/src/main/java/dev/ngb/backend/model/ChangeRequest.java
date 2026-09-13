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
 * One proposed change, travelling the maker-checker path.
 * <p>A configured value, a feature flag and an artifact another domain owns all travel the same
 * path,
 * so that the approval trail for publishing a policy and for changing a timeout look the same to
 * whoever has to audit them. The proposal freezes when it leaves draft, which is what stops
 * approval
 * of a small change being followed by an edit and application of a large one.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("change_requests")
public class ChangeRequest {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** What kind of thing is being changed. */
    private ChangeTargetKind targetKind;
    /** The setting being changed. */
    private @Nullable UUID configurationSettingId;
    /** The feature flag being changed. */
    private @Nullable UUID featureFlagDefinitionId;
    /** The domain owning the governed artifact being published. */
    private @Nullable String artifactDomain;
    /** What kind of governed artifact it is. */
    private @Nullable String artifactType;
    /** Reference to the artifact, held in the domain that owns it rather than copied here. */
    private @Nullable String artifactReference;
    /** Short name for the change, as it appears in the approval queue. */
    private String title;
    /** What the change is for and what it is expected to do. */
    private String intent;
    /** The value being proposed, for a configuration or flag change. */
    private @Nullable JsonDocument proposedValue;
    /** Digest of the value this change replaces, so a stale proposal is detectable. */
    private @Nullable String currentValueDigest;
    /** What this change reaches, which decides how it has to be approved. */
    private ChangeImpactClass impactClass;
    /** How far this change reaches. */
    private ChangeBlastRadius blastRadius;
    /** The markets this change touches. */
    private @Nullable String[] affectedMarkets;
    /** Which approval roles have to sign off before it can be applied. */
    private String[] requiredApprovalRoles;
    /** How this change would be undone; the sentence nobody wants to write during an incident. */
    private String rollbackPlan;
    /** Whether the change skipped the ordinary waiting period. */
    private boolean expedited;
    /** Why it could not wait, recorded at the time. */
    private @Nullable String expeditedJustification;
    /** UTC instant an expedited change owes its review by. */
    private @Nullable Instant expeditedReviewDueAt;
    /** The account holder who raised the change; never one of its approvers. */
    private UUID requestedBy;
    /** UTC instant it was raised. */
    private Instant requestedAt;
    /** Where the change stands on its way to being applied. */
    private ChangeRequestState requestState;
    /** UTC instant its checks passed. */
    private @Nullable Instant validatedAt;
    /** UTC instant the last required approval arrived. */
    private @Nullable Instant approvedAt;
    /** UTC instant it took effect. */
    private @Nullable Instant appliedAt;
    /** UTC instant it was refused. */
    private @Nullable Instant rejectedAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String rejectionReason;
    /** UTC instant the requester took it back. */
    private @Nullable Instant withdrawnAt;
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
