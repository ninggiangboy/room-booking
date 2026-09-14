package dev.ngb.backend.admin.internal.model.change;

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
import dev.ngb.backend.platform.JsonDocument;

import dev.ngb.backend.admin.internal.model.ConfigurationScopeType;
import dev.ngb.backend.platform.JsonDocument;


/**
 * What a flag was set to, at one scope, over one interval.
 * <p>The asymmetry is the point. Turning a flag off never needs a second person: the whole value of
 * a
 * kill switch is that whoever is holding the pager at three in the morning does not have to find
 * one.
 * Turning one back on always does.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("feature_flag_states")
public class FeatureFlagState {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The flag this state is of. */
    private UUID featureFlagDefinitionId;
    /** The scope this state applies at, which must be one the flag allows. */
    private ConfigurationScopeType scopeType;
    /** The organization, property or listing this state is for. */
    private @Nullable UUID scopeId;
    /** ISO 3166-1 alpha-2 market this state is for. */
    private @Nullable String marketCode;
    /** Whether the flag is on over this interval. */
    private boolean enabled;
    /** Fraction of traffic the flag is on for, when it is on for only some of it. */
    private @Nullable BigDecimal rolloutShare;
    /** Which traffic the flag applies to, for a flag that supports targeting. */
    private @Nullable JsonDocument targetingRule;
    /** Digest of the targeting rule, so it can be shown later to be unchanged. */
    private @Nullable String targetingDigest;
    /** UTC instant this state starts. */
    private Instant effectiveFrom;
    /** UTC instant it stops; open while it is the state in force. */
    private @Nullable Instant effectiveUntil;
    /** How this state came to be set; enabling is only ever a request or a rollback. */
    private FeatureFlagStateOrigin origin;
    /** The approved request behind it, required whenever the flag is being turned on. */
    private @Nullable UUID changeRequestId;
    /** The account holder who set it. */
    private UUID actorId;
    /** Approved reason code recording why; free text never stands in for one. */
    private String reasonCode;
    /** The incident an emergency pull was made for. */
    private @Nullable String incidentReference;
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
