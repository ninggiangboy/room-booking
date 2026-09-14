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
import dev.ngb.backend.platform.JsonDocument;

/**
 * One value a setting held over one interval.
 * <p>Append-only apart from being closed by its successor, and no two values of one setting may be
 * in
 * force at the same instant. A rollback is a new version pointing at the one it restores, because
 * the
 * incident review needs to read the value that was actually live.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("configuration_versions")
public class ConfigurationVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The setting this value belongs to. */
    private UUID configurationSettingId;
    /** Position of this value in the setting history, starting at one. */
    private int versionNumber;
    /** The configured value itself, conforming to its schema. */
    private JsonDocument value;
    /** Digest of the value, so it can be shown later to be unchanged. */
    private String valueDigest;
    /** UTC instant this value starts applying. */
    private Instant effectiveFrom;
    /** UTC instant it stops; open while it is the value in force. */
    private @Nullable Instant effectiveUntil;
    /** The approved request this value was applied under. */
    private @Nullable UUID changeRequestId;
    /** How this value came to exist. */
    private ConfigurationVersionOrigin origin;
    /** The value this one replaced. */
    private @Nullable UUID supersedesVersionId;
    /** The earlier value this one restores, for a rollback. */
    private @Nullable UUID rollbackOfVersionId;
    /** The account holder who applied it. */
    private UUID appliedBy;
    /** UTC instant it was applied, never after it became effective. */
    private Instant appliedAt;
    /** The value that replaced this one. */
    private @Nullable UUID supersededBy;
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
