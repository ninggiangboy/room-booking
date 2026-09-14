package dev.ngb.backend.support.internal.model.queue;

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

import dev.ngb.backend.support.internal.model.CaseSeverity;

/**
 * Deterministic eligibility and hard priority for routing work.
 *
 * <p>An optional model may order work that is already equivalent; the severity rank floor is what stops
 * a ranking change from hiding a safety case behind a lucrative one.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("routing_policy_versions")
public class RoutingPolicyVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the routing policy. */
    private String routingPolicyKey;
    /** Which version of the routing applies. */
    private int routingVersion;
    /** The market this row belongs to. */
    private @Nullable UUID marketId;
    /** Where the status stands. */
    private PolicyVersionStatus status;
    /** Effective from. */
    private @Nullable Instant effectiveFrom;
    /** Effective until. */
    private @Nullable Instant effectiveUntil;
    /** Reference to the eligibility rule, held in its owning system rather than copied here. */
    private String eligibilityRuleReference;
    /** Reference to the priority rule, held in its owning system rather than copied here. */
    private String priorityRuleReference;
    /** The rank below which a model may not reorder work. */
    private CaseSeverity severityRankFloor;
    /** Whether a model may order equivalent work at all. */
    private boolean modelOrderingPermitted;
    /** Reference to the model, held in its owning system rather than copied here. */
    private @Nullable String modelReference;
    /** Whether continuity preferred. */
    private boolean continuityPreferred;
    /** Conflict exclusion rules. */
    private String[] conflictExclusionRules;
    /** Stable key naming the overflow queue. */
    private @Nullable String overflowQueueKey;
    /** Overflow threshold. */
    private @Nullable Integer overflowThreshold;
    /** Escalation route. */
    private String escalationRoute;
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
