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
import org.springframework.data.relational.core.mapping.Table;
import dev.ngb.backend.platform.DataPrivacyClass;

/**
 * One action taken under emergency access.
 * <p>Append-only, and refused outside the window the grant was open for: an activity recorded
 * outside
 * it is either a clock problem or an access that did not happen under this grant, and both are
 * worth
 * refusing rather than filing.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("break_glass_activities")
public class BreakGlassActivity {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The emergency grant this action was taken under. */
    private UUID breakGlassGrantId;
    /** Position of this action within its grant, unique there. */
    private int sequenceNumber;
    /** UTC instant the action was taken; it must fall inside the open window. */
    private Instant occurredAt;
    /** Which permission was exercised. */
    private String permissionKey;
    /** The domain that owns what was touched. */
    private String owningDomain;
    /** Kind of thing that was touched. */
    private String targetType;
    /** The row that was touched. */
    private @Nullable UUID targetId;
    /** Reference to what was touched, for things with no row of their own. */
    private @Nullable String targetReference;
    /** Whether the action read, wrote, issued a command or exported. */
    private BreakGlassOperation operation;
    /** How many rows the action reached, so a bulk read is visible as one. */
    private int rowCount;
    /** The class of data the action reached. */
    private DataPrivacyClass dataSensitivity;
    /** The shared audit row this action also wrote. */
    private @Nullable UUID auditEventId;
    /** Opaque identifier of the request that carried it. */
    private @Nullable String requestId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
