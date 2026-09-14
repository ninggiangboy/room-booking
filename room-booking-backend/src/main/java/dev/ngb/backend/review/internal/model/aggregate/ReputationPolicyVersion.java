package dev.ngb.backend.review.internal.model.aggregate;

import java.math.BigDecimal;
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
 * One approved purpose reputation may be computed for.
 *
 * <p>This is the table that stops a general-purpose score existing. Nothing may be computed without a
 * purpose named here, and a published purpose must declare its consumers, its allowed inputs, its
 * minimum evidence, its prohibited proxies and the fairness evidence behind it -- all check
 * constraints, not conventions.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("reputation_policy_versions")
public class ReputationPolicyVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** What question this purpose answers. */
    private String purposeCode;
    /** Version within that purpose. */
    private int policyVersion;
    /** Who it is computed about. */
    private ReputationSubjectRole subjectRole;
    /** Who it is computed for. */
    private ReputationActorRole actorRole;
    /** What it may read. */
    private String[] allowedInputFamilies;
    /** What it may never read, including proxies. */
    private String[] prohibitedAttributes;
    /** Below this, the answer is insufficient evidence. */
    private int minimumEvidenceCount;
    /** Formula or rule version applied. */
    private String formulaVersion;
    /** Below this confidence, no answer is given. */
    private @Nullable BigDecimal confidenceFloor;
    /** How long an answer stays usable. */
    private short validityDays;
    /** Whether the subject must be able to see why. */
    private boolean explanationRequired;
    /** Whether the subject must be able to appeal. */
    private boolean appealRequired;
    /** Where the fairness release evidence lives. */
    private @Nullable String fairnessEvidenceReference;
    /** Who may consume the answer. */
    private String[] consumerAllowlist;
    /** How far this version has progressed. */
    private PolicyVersionStatus status;
    /** When it came into force. */
    private @Nullable Instant effectiveFrom;
    /** When it stopped being in force. */
    private @Nullable Instant effectiveUntil;
    /** Who owns the purpose. */
    private @Nullable UUID ownerAccountHolderId;
    /** Who approved it. */
    private @Nullable UUID approvedByAccountHolderId;
    /** When it was approved. */
    private @Nullable Instant approvedAt;
    /** Hash of the whole version. */
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
