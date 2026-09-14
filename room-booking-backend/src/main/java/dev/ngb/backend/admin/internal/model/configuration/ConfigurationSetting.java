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

import dev.ngb.backend.admin.internal.model.ConfigurationScopeType;


/**
 * One addressable configuration setting: a schema at a scope, with a resolution priority and an
 * owner.
 * <p>The schema, the scope, the priority and the owner belong to the setting; the effective
 * interval
 * and the version belong to the value. Splitting them is what lets the resolver answer which
 * setting
 * won and the auditor answer what it was set to in March, separately.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("configuration_settings")
public class ConfigurationSetting {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The schema version this setting conforms to. */
    private UUID configurationSchemaId;
    /** The schema key, denormalized because the resolver reads it. */
    private String schemaKey;
    /** The scope this setting applies at, which must be one the schema allows. */
    private ConfigurationScopeType scopeType;
    /** The organization, property or listing this setting is for. */
    private @Nullable UUID scopeId;
    /** ISO 3166-1 alpha-2 market this setting is for. */
    private @Nullable String marketCode;
    /** Where this setting sits in the resolution order; the global setting is always zero. */
    private int resolutionPriority;
    /** The team accountable for this particular setting. */
    private String ownerReference;
    /** What this setting is for, distinguishing it from the others under the same schema. */
    private String description;
    /** Whether the setting still resolves. */
    private ConfigurationSettingState settingState;
    /** UTC instant the setting stopped resolving. */
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
