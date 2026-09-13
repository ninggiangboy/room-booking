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
 * A request to look again at one effective decision, restriction or moderation outcome.
 *
 * <p>An appeal is not a second opinion from the same person: the original decider is read from the
 * target by trigger rather than supplied, and the assigned reviewer must differ from them. Intake
 * acknowledges receipt without promising reversal. Deciding requires findings; granting requires a
 * prospective restoration date, because success restores capability going forward and does not erase
 * history or by itself create a monetary entitlement.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_appeals")
public class RiskAppeal {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** What is being appealed. */
    private AppealTargetKind targetKind;
    /** The decision, when that is the target. */
    private @Nullable UUID riskDecisionId;
    /** The restriction, when that is the target. */
    private @Nullable UUID riskRestrictionId;
    /** The moderation decision, when that is the target. */
    private @Nullable UUID moderationDecisionId;
    /** Who is appealing. */
    private UUID appellantSubjectId;
    /** On what basis they may. */
    private AppellantAuthority appellantAuthority;
    /** Which round; one appeal per target, appellant and round. */
    private short appealRound;
    /** The grounds they submitted. */
    private String groundsReference;
    /** New or clarified evidence. */
    private String[] evidenceReferences;
    /** Language the appeal was submitted in. */
    private @Nullable String languageTag;
    /** Where it stands. */
    private RiskAppealState state;
    /** When it must be answered by. */
    private Instant deadlineAt;
    /** Who decided the appealed matter; read from the target. */
    private @Nullable UUID originalDeciderAccountHolderId;
    /** Who is hearing it; never the original decider. */
    private @Nullable UUID assignedReviewerAccountHolderId;
    /** The review task carrying the work. */
    private @Nullable UUID riskReviewTaskId;
    /** How it was answered. */
    private @Nullable RiskAppealOutcome outcome;
    /** The reviewer's findings; required to decide. */
    private @Nullable String findingsReference;
    /** The decision that replaced the original, where one did. */
    private @Nullable UUID supersedingDecisionId;
    /** When restored capability takes effect; required for a grant. */
    private @Nullable Instant restorationEffectiveFrom;
    /** The support case any remedy was referred to; risk decides no money. */
    private @Nullable String remedyReferralReference;
    /** When it was received. */
    private Instant submittedAt;
    /** When receipt was confirmed. */
    private @Nullable Instant acknowledgedAt;
    /** When it was answered. */
    private @Nullable Instant decidedAt;
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
