package dev.ngb.backend.admin.internal.model.change;

import java.time.Instant;
import java.time.LocalDate;
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
import dev.ngb.backend.platform.GovernedRegistryStatus;

/**
 * What a feature flag is, who may turn it on, and the date it is gone by.
 * <p>Every flag carries an expiry. Extending one is allowed and is recorded as an extension with a
 * reason, because a flag on its fourth extension is product behaviour that never went through a
 * product decision.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("feature_flag_definitions")
public class FeatureFlagDefinition {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the flag, as the runtime reads it. */
    private String flagKey;
    /** Human-readable name shown in the administrative console. */
    private String displayName;
    /** What the flag is for; the rules about moving it differ by kind. */
    private FeatureFlagKind flagKind;
    /** The domain whose behaviour the flag changes. */
    private String owningDomain;
    /** The team accountable for the flag and for its removal. */
    private String ownerReference;
    /** What the flag does, in the words somebody deciding whether to pull it will read. */
    private String purpose;
    /** The state the flag was registered with; a kill switch is always off by default. */
    private boolean defaultEnabled;
    /** The scopes a state of this flag may be set at. */
    private String[] allowedScopeTypes;
    /** Whether a state of this flag may carry a targeting rule. */
    private boolean targetingSupported;
    /**
     * What a kill switch turns off; a switch whose effect nobody wrote down is one nobody dares
     * pull.
     */
    private @Nullable String disablesCapability;
    /** Which approval roles have to sign off on turning the flag on. */
    private String[] enableApprovalRoles;
    /**
     * The date the flag is gone by; a permanent temporary flag is undocumented product behaviour.
     */
    private LocalDate expiresOn;
    /** How many times the expiry has been pushed out. */
    private int extensionCount;
    /** Why the expiry moved, recorded each time it does. */
    private @Nullable String extensionReason;
    /** Where the flag is documented. */
    private String documentationReference;
    /** Where the flag stands in its own lifecycle. */
    private GovernedRegistryStatus status;
    /** UTC instant the flag became usable. */
    private @Nullable Instant activatedAt;
    /** UTC instant the flag was marked for removal. */
    private @Nullable Instant deprecatedAt;
    /** UTC instant the flag stopped being usable. */
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
