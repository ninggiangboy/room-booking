package dev.ngb.backend.admin.internal.model.configuration;

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
import dev.ngb.backend.platform.GovernedRegistryStatus;
import dev.ngb.backend.platform.JsonDocument;

import dev.ngb.backend.admin.internal.model.ChangeBlastRadius;
import dev.ngb.backend.admin.internal.model.ChangeImpactClass;
import dev.ngb.backend.platform.GovernedRegistryStatus;
import dev.ngb.backend.platform.JsonDocument;


/**
 * What a configurable thing is, and how it may be changed.
 * <p>Carries the value schema, the scopes it makes sense at, what a wrong value here reaches, and
 * the
 * evidence and approvals a change requires. A value touching money, identity, safety or a regulator
 * is never changed by one person, whatever the schema would prefer.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("configuration_schemas")
public class ConfigurationSchema {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the configurable thing, the same across every version of its schema. */
    private String schemaKey;
    /** Which version of this schema key the row is. */
    private int schemaVersion;
    /** Human-readable name shown in the administrative console. */
    private String displayName;
    /** The domain that owns the behaviour this setting changes. */
    private String owningDomain;
    /** What shape a value of this setting takes. */
    private ConfigurationValueShape valueShape;
    /** Where the full value schema is held. */
    private String valueSchemaReference;
    /** Digest of the value schema, so it can be shown later to be unchanged. */
    private String valueSchemaDigest;
    /** The scopes a setting under this schema may be created at. */
    private String[] allowedScopeTypes;
    /** The value that applies when no setting resolves. */
    private JsonDocument defaultValue;
    /** What a wrong value here reaches, which decides how it has to be approved. */
    private ChangeImpactClass impactClass;
    /** How far a change under this schema reaches. */
    private ChangeBlastRadius blastRadius;
    /** Whether a change needs a second person; money, identity and safety always do. */
    private boolean requiresMakerChecker;
    /** Which approval roles have to sign off on a change. */
    private String[] requiredApprovalRoles;
    /** Whether a change has to be simulated against recorded traffic before approval. */
    private boolean requiresSimulation;
    /** Whether a change has to be previewed as a user would see it before approval. */
    private boolean requiresPreview;
    /** Whether a change can be undone by returning to an earlier value. */
    private boolean rollbackSupported;
    /** Why it cannot be rolled back; the sentence somebody needs during the incident. */
    private @Nullable String rollbackNote;
    /** Whether a change here is held during a change freeze. */
    private boolean changeFreezeApplies;
    /** The team accountable for this setting, held in the directory rather than here. */
    private String ownerReference;
    /** Where the setting is documented. */
    private String documentationReference;
    /** The earlier version of this schema that this one replaces. */
    private @Nullable UUID supersedesId;
    /** Where the schema stands in its own lifecycle. */
    private GovernedRegistryStatus status;
    /** UTC instant the schema became usable. */
    private @Nullable Instant activatedAt;
    /** UTC instant the schema was marked for replacement. */
    private @Nullable Instant deprecatedAt;
    /** UTC instant the schema stopped being usable. */
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
