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
 * What one person is permitted to hold, bounded in time.
 *
 * <p>Revocation closes an interval rather than deleting the row, because who could have acted on a case
 * last March must stay answerable.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("agent_skill_grants")
public class AgentSkillGrant {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The account holder this row belongs to. */
    private UUID accountHolderId;
    /** Skill code. */
    private String skillCode;
    /** Role code. */
    private String roleCode;
    /** The authority policy version this row belongs to. */
    private UUID authorityPolicyVersionId;
    /** The market this row belongs to. */
    private @Nullable UUID marketId;
    /** The legal entity this row belongs to. */
    private @Nullable UUID legalEntityId;
    /** Where the state stands. */
    private SkillGrantState state;
    /** UTC instant granted. */
    private Instant grantedAt;
    /** Effective until. */
    private @Nullable Instant effectiveUntil;
    /** UTC instant revoked. */
    private @Nullable Instant revokedAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String revocationReason;
    /** The granted by account holder this row belongs to. */
    private UUID grantedByAccountHolderId;
    /** Reference to the certification, held in its owning system rather than copied here. */
    private @Nullable String certificationReference;
    /** UTC instant the certification behind the grant lapses. */
    private @Nullable Instant certificationExpiresAt;
    /** Languages. */
    private String[] languages;
    /** The grant this one was delegated from; a delegation cannot exceed it. */
    private @Nullable UUID delegatedFromGrantId;
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
