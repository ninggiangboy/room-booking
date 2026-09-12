package dev.ngb.backend.model;

import java.time.Instant;
import java.time.LocalTime;
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
 * What one person chose about optional notices, per category and channel.
 *
 * <p>Nothing here suppresses a mandatory transactional notice: that decision belongs to the policy
 * classification. Quiet hours are stored as a window in a named IANA zone, never as an offset, so
 * they still mean the same thing after a daylight-saving change.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("notification_preferences")
public class NotificationPreference {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Whose preference this is. */
    private UUID accountHolderId;
    /** Category the preference covers. */
    private String categoryCode;
    /** Specific purpose it overrides; null makes it the category default. */
    private @Nullable String purposeCode;
    /** Channel the preference applies to. */
    private NotificationChannel channel;
    /** Whether optional notices of this kind may be sent. */
    private boolean enabled;
    /** Local wall time quiet hours begin. */
    private @Nullable LocalTime quietHoursStart;
    /** Local wall time quiet hours end. */
    private @Nullable LocalTime quietHoursEnd;
    /** IANA zone those wall times are read in. */
    private @Nullable String quietHoursZone;
    /** Who set the preference. */
    private PreferenceSource preferenceSource;
    /** Case or import reference behind a preference somebody else set. */
    private @Nullable String sourceReference;
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
     * Whether this row is the category-wide default rather than a purpose override.
     *
     * @return {@code true} when no purpose is named
     */
    public boolean isCategoryDefault() {
        return purposeCode == null;
    }
    /**
     * Whether a quiet-hours window is configured.
     *
     * @return {@code true} when a window and its zone are both present
     */
    public boolean hasQuietHours() {
        return quietHoursStart != null && quietHoursEnd != null && quietHoursZone != null;
    }
}
