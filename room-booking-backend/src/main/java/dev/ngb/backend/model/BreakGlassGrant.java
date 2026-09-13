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
 * Emergency access, bounded and owed a review.
 * <p>It names the incident, is approved by somebody other than the person asking, expires by an
 * interval the role itself caps, alerts when it opens, records every target it touched, and owes a
 * post-use review by a third person. Its facts are fixed once granted, because the only person
 * likely
 * to want to edit them is the one the review is about.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("break_glass_grants")
public class BreakGlassGrant {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The role version being granted in the emergency. */
    private UUID roleDefinitionId;
    /** The account holder receiving the emergency access. */
    private UUID operatorId;
    /** ISO 3166-1 alpha-2 market the access is limited to. */
    private @Nullable String marketCode;
    /** What kind of emergency justified breaking the glass. */
    private BreakGlassTrigger triggerKind;
    /** The incident this access was opened for, so the review can find it. */
    private String incidentReference;
    /** Why ordinary authority was not enough, written at the time rather than afterwards. */
    private String justification;
    /** UTC instant the access was asked for. */
    private Instant requestedAt;
    /** The account holder who approved it, never the operator asking. */
    private UUID approvedBy;
    /** UTC instant it was approved. */
    private Instant approvedAt;
    /** How the approval was given, since a page or a call is legitimate at three in the morning. */
    private BreakGlassApprovalChannel approvalChannel;
    /** UTC instant the access opened. */
    private Instant grantedAt;
    /** UTC instant it closes on its own, capped by the role. */
    private Instant expiresAt;
    /** UTC instant the alert went out; an elevation nobody was told about is a quiet one. */
    private Instant alertDispatchedAt;
    /** The capability grant that carried the access, held in migration 014. */
    private @Nullable UUID capabilityGrantId;
    /** UTC instant the access ended, whether by expiry, surrender or revocation. */
    private @Nullable Instant closedAt;
    /** How the access ended. */
    private @Nullable BreakGlassClosureReason closureReason;
    /** How many actions were taken under the grant, maintained by the database. */
    private int activityCount;
    /** UTC instant the post-use review is owed by. */
    private Instant reviewDueAt;
    /** Where the post-use review stands. */
    private BreakGlassReviewState reviewState;
    /** The account holder who reviewed it, neither the operator nor the approver. */
    private @Nullable UUID reviewedBy;
    /** UTC instant the review concluded. */
    private @Nullable Instant reviewedAt;
    /** What the review concluded about the access that was taken. */
    private @Nullable BreakGlassFinding reviewFinding;
    /** What the reviewer wrote, for the finding that needs more than a code. */
    private @Nullable String reviewNotes;
    /** The follow-up action a finding other than appropriate requires. */
    private @Nullable String followUpReference;
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
