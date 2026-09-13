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
 * The coordination aggregate: one problem scope, its participants, its deadlines and its decisions.
 *
 * <p>Classification lives in controlled dimensions rather than one overloaded type, and safety, waiting
 * party, financial execution, appeal and legal hold are separate columns because a case can be
 * investigating while a provider deadline expires and a refund outcome is unknown.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("support_cases")
public class SupportCase {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Human-quotable reference, unique across the platform. */
    private String caseReference;
    /** The market this row belongs to. */
    private UUID marketId;
    /** The legal entity this row belongs to. */
    private @Nullable UUID legalEntityId;
    /** The primary booking this row belongs to. */
    private @Nullable UUID primaryBookingId;
    /** The primary listing this row belongs to. */
    private @Nullable UUID primaryListingId;
    /** The primary property this row belongs to. */
    private @Nullable UUID primaryPropertyId;
    /** The primary conversation this row belongs to. */
    private @Nullable UUID primaryConversationId;
    /** The primary incident this row belongs to. */
    private @Nullable UUID primaryIncidentId;
    /** The primary payment dispute this row belongs to. */
    private @Nullable UUID primaryPaymentDisputeId;
    /** Source channel. */
    private CaseSourceChannel sourceChannel;
    /** The client command that opened the case; a retry converges on the same case. */
    private @Nullable String intakeCommandId;
    /** Whether the report was filed without an account behind it. */
    private boolean anonymousReport;
    /** The reporter account holder this row belongs to. */
    private @Nullable UUID reporterAccountHolderId;
    /** Which reporter role this row carries. */
    private CaseReporterRole reporterRole;
    /** The represented party account holder this row belongs to. */
    private @Nullable UUID representedPartyAccountHolderId;
    /** Where the reporter’s own words are held; this domain stores the reference, not the text. */
    private @Nullable String reportTextReference;
    /** BCP 47 locale the content is written in. */
    private String locale;
    /** Contact preference. */
    private CaseContactPreference contactPreference;
    /** Which version of the classification vocabulary the dimensions were chosen from. */
    private int taxonomyVersion;
    /** Which case type this row carries. */
    private SupportCaseType caseType;
    /** Journey stage. */
    private CaseJourneyStage journeyStage;
    /** Issue family. */
    private CaseIssueFamily issueFamily;
    /** Which request kind this row carries. */
    private CaseRequestKind requestKind;
    /** Which impact class this row carries. */
    private CaseImpactClass impactClass;
    /** Where the responsibility stands. */
    private ResponsibilityStatus responsibilityStatus;
    /** Which sensitivity class this row carries. */
    private CaseSensitivityClass sensitivityClass;
    /** Severity. */
    private CaseSeverity severity;
    /** Numeric rank of the severity, pinned to the label so ordering and naming cannot drift. */
    private short severityRank;
    /** The minimum deterministic intake established; severity may never fall below it. */
    private CaseSeverity severityFloor;
    /** Why severity was lowered, which lowering always requires. */
    private @Nullable String severityChangeReason;
    /** Where the state stands. */
    private SupportCaseState state;
    /** Which episode of the case this is; reopening starts a new one and preserves the closure. */
    private short lifecycleEpisode;
    /** The support queue this row belongs to. */
    private @Nullable UUID supportQueueId;
    /** Owner team. */
    private @Nullable String ownerTeam;
    /** The owner account holder this row belongs to. */
    private @Nullable UUID ownerAccountHolderId;
    /** Ordering hint within a severity band; it can never lift a case above a more severe one. */
    private int priority;
    /** Where immediate safety handling stands, independent of the case state. */
    private CaseSafetyState safetyState;
    /** Who the case is waiting for, which is never inferred from a generic pending status. */
    private CaseWaitingOn waitingOn;
    /** Where downstream money stands, independent of the case state. */
    private FinancialExecutionState financialExecutionState;
    /** Where an appeal stands, independent of the case state. */
    private CaseAppealProgress appealState;
    /** UTC instant an external provider deadline expires, if one is running. */
    private @Nullable Instant providerDeadlineAt;
    /** Whether a legal hold is in force on this case. */
    private boolean legalHold;
    /** The hold that is in force, which a hold always has to name. */
    private @Nullable String legalHoldReference;
    /** UTC instant occurrence. */
    private @Nullable Instant occurrenceAt;
    /** UTC instant received. */
    private Instant receivedAt;
    /** UTC instant opened. */
    private Instant openedAt;
    /** UTC instant first response. */
    private @Nullable Instant firstResponseAt;
    /** UTC instant resolved. */
    private @Nullable Instant resolvedAt;
    /** UTC instant closed. */
    private @Nullable Instant closedAt;
    /** UTC instant reopened. */
    private @Nullable Instant reopenedAt;
    /** UTC instant withdrawn. */
    private @Nullable Instant withdrawnAt;
    /** The support policy version this row belongs to. */
    private @Nullable UUID supportPolicyVersionId;
    /** The investigation template version this row belongs to. */
    private @Nullable UUID investigationTemplateVersionId;
    /** The routing policy version this row belongs to. */
    private @Nullable UUID routingPolicyVersionId;
    /** The most recent decision on the case, for display only; authority stays on the decision row. */
    private @Nullable UUID latestDecisionId;
    /** The case this one duplicates, set only while the state says so. */
    private @Nullable UUID duplicateOfCaseId;
    /** Who owns the unfinished work a closure left behind. */
    private @Nullable UUID closureExceptionOwnerAccountHolderId;
    /** Why closure was permitted with work outstanding. */
    private @Nullable String closureExceptionReason;
    /** UTC instant somebody must look at that work again. */
    private @Nullable Instant closureExceptionDeadlineAt;
    /** Which retention class this row carries. */
    private CaseRetentionClass retentionClass;
    /** Whether the case has been drawn into quality review sampling. */
    private boolean qualitySampled;
    /** Next transition sequence number to allocate for this case. */
    private long nextSequence;
    /** Whether the case was opened here or imported from legacy tooling. */
    private CaseSourceSystem sourceSystem;
    /** Digest of the legacy artifact an imported case was proven from. */
    private @Nullable String importDigest;
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
