package dev.ngb.backend.trust.internal.model.intervention;

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
import org.springframework.data.relational.core.mapping.Table;


/**
 * Who looked at protected risk evidence, why, and under what authority.
 *
 * <p>Migration 012's audit stream records what changed; this records what was read, which is the half
 * that matters when the abuse is a reviewer browsing rather than a reviewer acting. An export needs an
 * approval reference and a record count, and break-glass always carries its grant and always earns a
 * post-use review. Append-only.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_access_audit")
public class RiskAccessAudit {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Who accessed it. */
    private UUID actorAccountHolderId;
    /** The session they did it from. */
    private @Nullable UUID authSessionId;
    /** What they did. */
    private RiskAccessKind accessKind;
    /** What kind of evidence. */
    private String resourceType;
    /** Which record, for a single-record access. */
    private @Nullable UUID resourceId;
    /** The selector used, for a query or export. */
    private @Nullable String resourceSelector;
    /** How many records were returned; required for an export. */
    private @Nullable Integer recordCount;
    /** The purpose declared before access. */
    private String declaredPurpose;
    /** Why that purpose applied here. */
    private String purposeReason;
    /** The task the access was performed for. */
    private @Nullable UUID riskReviewTaskId;
    /** The approval behind a bulk disclosure. */
    private @Nullable String approvalReference;
    /** Whether ordinary authorization was bypassed. */
    private boolean breakGlass;
    /** The break-glass grant used. */
    private @Nullable String breakGlassReference;
    /** Whether this access must be reviewed afterwards; always true for break-glass. */
    private boolean postUseReviewRequired;
    /** Whether the access was permitted. */
    private RiskAccessOutcome outcome;
    /** When it happened. */
    private Instant accessedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
