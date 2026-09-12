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
 * The words themselves, per channel and locale, immutable once approved.
 *
 * <p>The variable schema is the contract rendering is checked against. A missing required variable
 * fails the render into an operations queue and is never invented, which is why the schema lives
 * with the approved artifact rather than in code.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("notification_templates")
public class NotificationTemplate {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable identity of the template family. */
    private String templateFamily;
    /** Version within that family. */
    private Integer templateVersion;
    /** Channel these words are written for. */
    private NotificationChannel channel;
    /** Locale they are written in. */
    private String locale;
    /** Market this version is scoped to; null means every market. */
    private @Nullable String marketCode;
    /** Purpose the template serves. */
    private String purposeCode;
    /** Whether consent and quiet hours apply. */
    private NotificationClassification classification;
    /** Subject or title; required for email. */
    private @Nullable String subjectText;
    /** Body, with typed variable placeholders. */
    private String bodyText;
    /** Safe preview variant, where the channel shows one. */
    private @Nullable String previewText;
    /** Typed variables, their classification and whether each is required. */
    private JsonDocument variableSchema;
    /** Links and actions the template offers, with their expiry rules. */
    private @Nullable JsonDocument actionSchema;
    /** How variables must be escaped when rendered. */
    private TemplateEscapingRule escapingRule;
    /** Approval lifecycle. */
    private NotificationArtifactStatus status;
    /** When this version starts being selectable. */
    private @Nullable Instant effectiveFrom;
    /** When it stops. */
    private @Nullable Instant effectiveUntil;
    /** Who approved it. */
    private @Nullable UUID approvedByAccountId;
    /** When they approved it. */
    private @Nullable Instant approvedAt;
    /** Evidence of that approval. */
    private @Nullable String approvalEvidenceReference;
    /** Version to fall back to if this one has to be withdrawn. */
    private @Nullable UUID rollbackPredecessorId;
    /** SHA-256 of the words, cited by every render made from them. */
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

    /**
     * Whether new renders may use this version.
     *
     * @return {@code true} only while the template is active
     */
    public boolean isSelectable() {
        return status == NotificationArtifactStatus.ACTIVE;
    }
}
