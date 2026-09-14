package dev.ngb.backend.admin.internal.model.role;

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
import dev.ngb.backend.platform.DataPrivacyClass;
import dev.ngb.backend.platform.GovernedRegistryStatus;

/**
 * What an operator role means, kept apart from the grant that confers it.
 * <p>The grant that actually opens the door is migration 014's capability grant. This row says what
 * the role is for, how far it reaches, how long it may be held, what training it needs and whether
 * it
 * may ever be taken under emergency access. Keeping the definition apart from the grant is what
 * makes
 * it possible to ask what a role meant last March, after the role has been narrowed twice
 * since.</p>
 * <p>Frozen past {@code DRAFT}: widening a live role would retroactively widen every grant already
 * made under it, and nothing in the approval trail would show it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("operator_role_definitions")
public class OperatorRoleDefinition {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable upper-case key naming the role, the same across every version of it. */
    private String roleKey;
    /** Which version of this role key the row is; a new version is how a role changes. */
    private int roleVersion;
    /** Human-readable name shown in the administrative console. */
    private String displayName;
    /** What the role is for, in the words its approver will read before granting it. */
    private String purpose;
    /** Blast radius of the role, which decides how many approvals an assignment needs. */
    private OperatorAuthorityClass authorityClass;
    /** Whether the role is held globally or only within one market. */
    private OperatorMarketScope marketScope;
    /** The most sensitive class of data any permission in this role reaches. */
    private DataPrivacyClass dataSensitivity;
    /** Longest an assignment of this role may run; a standing authority is refused. */
    private int maximumGrantDays;
    /** How soon an assignment must be re-examined by somebody other than its holder. */
    private int recertificationDays;
    /** Whether an assignment must record a training attestation. */
    private boolean requiresTraining;
    /** The training an assignment has to attest to, held where the training lives. */
    private @Nullable String trainingReference;
    /** Which approval role has to sign off on an assignment of this role. */
    private String assignmentApprovalRole;
    /** Whether this role may ever be granted as emergency access. */
    private boolean breakGlassEligible;
    /** Longest an emergency grant of this role may run before it expires. */
    private @Nullable Integer breakGlassMaximumMinutes;
    /** How soon after expiry an emergency grant owes a post-use review. */
    private @Nullable Integer breakGlassReviewHours;
    /** The team accountable for what this role can do, held in the directory rather than here. */
    private String ownerReference;
    /** The earlier version of this role that this one replaces. */
    private @Nullable UUID supersedesId;
    /** Where the role definition stands in its own lifecycle. */
    private GovernedRegistryStatus status;
    /** UTC instant the role became grantable. */
    private @Nullable Instant activatedAt;
    /** UTC instant the role was marked for replacement. */
    private @Nullable Instant deprecatedAt;
    /** UTC instant the role stopped being grantable. */
    private @Nullable Instant retiredAt;
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
