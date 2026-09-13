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
 * The governed intervention behind an enforced limitation.
 *
 * <p>Migration 014's {@code capability_restrictions} is what authorization evaluates on every
 * request; this row is the reason it exists, carrying the policy, decision, review date and appeal
 * path a bare capability row cannot. Risk does not write that row -- it names the one identity wrote
 * to honour this.</p>
 *
 * <p>One live restriction per target, scope and intent. Once in force, what it restricts is frozen
 * and its end may be brought forward but never pushed back, because a late expiry worker extending a
 * restriction is indistinguishable from a punishment nobody decided.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_restrictions")
public class RiskRestriction {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Who or what is restricted. */
    private UUID targetSubjectId;
    /** What is stopped. */
    private RestrictionType restrictionType;
    /** The capability the restriction bites on. */
    private String capabilityScope;
    /** What kind of resource it is narrowed to. */
    private @Nullable String resourceType;
    /** Which resource. */
    private @Nullable UUID resourceId;
    /** Why it exists; part of the live uniqueness so two intents may coexist. */
    private String policyIntent;
    /** Stable internal reason. */
    private String reasonCode;
    /** The approved family the subject may be told; required when high impact. */
    private @Nullable String userReasonFamily;
    /** Policy version behind it. */
    private @Nullable UUID riskPolicyId;
    /** Decision behind it. */
    private @Nullable UUID riskDecisionId;
    /** Where it came from; a legacy backfill may claim no policy or decision. */
    private RestrictionOrigin origin;
    /** How firmly it is applied. */
    private RestrictionEnforcementMode enforcementMode;
    /** The authorization row identity wrote to honour this. */
    private @Nullable UUID capabilityRestrictionId;
    /** Where it stands. */
    private RestrictionState state;
    /** Whether it is one of the interventions that must carry a reason and an appeal path. */
    private boolean highImpact;
    /** Whether it is declared permanent rather than bounded. */
    private boolean permanent;
    /** Whether it can be appealed. */
    private boolean appealAvailable;
    /** The documented hazard behind withholding an appeal path. */
    private @Nullable String appealSuppressionReason;
    /** When it begins. */
    private Instant effectiveFrom;
    /** When it ends; may be brought forward, never extended. */
    private @Nullable Instant effectiveUntil;
    /** When it must be looked at again. */
    private @Nullable Instant reviewDueAt;
    /** When it came into force. */
    private @Nullable Instant activatedAt;
    /** When it was lifted. */
    private @Nullable Instant revokedAt;
    /** Why it was lifted. */
    private @Nullable String revocationReason;
    /** Who lifted it. */
    private @Nullable UUID revokedByAccountHolderId;
    /** The restriction that replaced it. */
    private @Nullable UUID supersededByRestrictionId;
    /** Who issued it, where a person did. */
    private @Nullable UUID createdByAccountHolderId;
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
