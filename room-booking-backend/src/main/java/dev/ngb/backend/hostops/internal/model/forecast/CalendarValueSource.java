package dev.ngb.backend.hostops.internal.model.forecast;

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


/**
 * Which layer set the value a host sees on one night, and which layer it beat.
 * <p>The domain document's rule is that a host can see the active source of each calendar value;
 * without this row the answer is "re-run the resolver and see what it says today", which is not the
 * question the host asked, because the inputs have moved since. A value the host cannot change
 * carries the reason on the same row, because a greyed-out cell with no explanation is the calendar
 * telling a host their own listing is not theirs.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("calendar_value_sources")
public class CalendarValueSource {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The inventory resource whose calendar this value belongs to. */
    private UUID inventoryResourceId;
    /** The night the value applies to. */
    private LocalDate stayDate;
    /** Which calendar value this row explains. */
    private CalendarValueKind valueKind;
    /** The layer that actually set the value the host sees. */
    private CalendarSourceLayer activeSource;
    /** What kind of row the active source points at. */
    private @Nullable String sourceReferenceKind;
    /**
     * The row that set the value, so the host can be shown the rule rather than told that a rule
     * exists.
     */
    private @Nullable UUID sourceReferenceId;
    /** The layer this one beat, where it beat one. */
    private @Nullable CalendarSourceLayer overriddenSource;
    /**
     * Where the winning layer sits in the resolution order; the order is data rather than a
     * property of the resolvers source code.
     */
    private short precedenceRank;
    /** The value as it is shown on the calendar. */
    private String resolvedDisplayValue;
    /** The value in integer minor units, for money values. */
    private @Nullable Long resolvedAmountMinor;
    /** ISO 4217 alphabetic code the minor-unit value is denominated in. */
    private @Nullable String currency;
    /** Whether the host may change this value. */
    private boolean hostEditable;
    /** Approved reason code explaining why the host may not change it. */
    private @Nullable String lockedReason;
    /** UTC instant this resolution took effect. */
    private Instant effectiveFrom;
    /** UTC instant the resolver last computed it. */
    private Instant resolvedAt;
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
