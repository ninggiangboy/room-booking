package dev.ngb.backend.model;

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
 * One due component of a collection: a deposit, a pay-now amount, or a later balance.
 *
 * <p>Components exist so that an installment plan is not three attempts against one total. Each
 * carries its own due date, state, and attempt history, and a balance that fails years later does
 * not erase the confirmation the deposit bought.</p>
 *
 * <p>Every component of one schedule version must sum to its obligation. No row can check that
 * alone, so it is enforced by a deferred constraint trigger that runs when the transaction
 * commits.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("collection_schedule_items")
public class CollectionScheduleItem {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Obligation this component belongs to. */
    private UUID obligationId;
    /** Schedule generation; a modification adds a version rather than editing one. */
    private int scheduleVersion;
    /** Position within the schedule version. */
    private short sequenceNumber;
    /** Role of this component. */
    private ScheduleComponentType componentType;
    /** ISO 4217 code, matching the obligation. */
    private String currency;
    /** Amount due for this component, in minor units. */
    private long amountMinor;
    /** Minor units collected against it. */
    private long satisfiedAmountMinor;
    /** UTC instant the platform acts on. */
    private Instant dueAt;
    /**
     * Civil date the guest was shown.
     *
     * <p>A guest is told "due on 3 March", not an instant. Both are stored: what the guest saw, and
     * what the platform acts on.</p>
     */
    private @Nullable LocalDate dueLocalDate;
    /** IANA zone that turns the civil date into the instant. */
    private @Nullable String dueTimezone;
    /** What this component must satisfy before inventory is confirmed. */
    private ConfirmationCondition confirmationCondition;
    /** Progress of this component. */
    private ScheduleItemState state;
    /** Collection attempts made against it. */
    private int attemptCount;
    /** UTC instant the dunning worker should try again. */
    private @Nullable Instant nextAttemptAt;
    /** UTC instant after which it defaults. */
    private @Nullable Instant finalDeadlineAt;
    /** Version of the retry policy governing attempts. */
    private @Nullable String retryPolicyVersion;
    /** Version of the policy governing what default means. */
    private @Nullable String defaultPolicyVersion;
    /** Component from an earlier schedule version this one supersedes. */
    private @Nullable UUID replacesItemId;
    /** UTC instant it was collected in full. */
    private @Nullable Instant satisfiedAt;
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
