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
 * The rule that turns a committed domain fact into an intent to tell somebody.
 *
 * <p>Immutable once approved, because "why was I sent this, and who signed it off" has to be
 * answerable with a document that still says what it said at the time. Lifecycle may still move --
 * approved to active to retired -- but the rules themselves never change; new wording is a new
 * version.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("notification_policies")
public class NotificationPolicy {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable identity of the policy family. */
    private String policyKey;
    /** Version within that family. */
    private Integer policyVersion;
    /** What the notice is for. */
    private String purposeCode;
    /** Preference and consent category the purpose belongs to. */
    private String categoryCode;
    /** Whether consent and quiet hours apply. */
    private NotificationClassification classification;
    /** Committed event type this policy reacts to. */
    private String sourceEventType;
    /** Market whose rules this version encodes; null means every market. */
    private @Nullable String marketCode;
    /** Who is eligible to receive the notice. */
    private JsonDocument recipientRule;
    /** Which channels may be used, and in what order. */
    private JsonDocument channelRule;
    /** When the notice may be sent relative to the fact. */
    private JsonDocument timingRule;
    /** What to do when the first route does not deliver. */
    private @Nullable JsonDocument fallbackRule;
    /** How quiet hours apply to this purpose. */
    private @Nullable JsonDocument quietHoursRule;
    /** Typed facts a template of this family may use. */
    private JsonDocument variableContract;
    /** Template family this policy renders with. */
    private String templateFamily;
    /** How the logical intent key is built. */
    private String deduplicationKeyTemplate;
    /** How long the notice stays useful after the fact. */
    private @Nullable Integer expirySeconds;
    /** Approval lifecycle. */
    private NotificationArtifactStatus status;
    /** When this version starts applying. */
    private @Nullable Instant effectiveFrom;
    /** When it stops applying. */
    private @Nullable Instant effectiveUntil;
    /** Who approved it. */
    private @Nullable UUID approvedByAccountId;
    /** When they approved it. */
    private @Nullable Instant approvedAt;
    /** Evidence of that approval. */
    private @Nullable String approvalEvidenceReference;
    /** SHA-256 of the rule set, so a stored intent can cite exactly what applied. */
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
     * Whether new intents may be created from this version.
     *
     * @return {@code true} only while the policy is active
     */
    public boolean isSelectable() {
        return status == NotificationArtifactStatus.ACTIVE;
    }
    /**
     * Whether this version governs the given instant.
     *
     * @param at instant being evaluated
     * @return {@code true} when the effective interval contains the instant
     */
    public boolean isEffectiveAt(Instant at) {
        return effectiveFrom != null && !effectiveFrom.isAfter(at)
                && (effectiveUntil == null || effectiveUntil.isAfter(at));
    }
}
