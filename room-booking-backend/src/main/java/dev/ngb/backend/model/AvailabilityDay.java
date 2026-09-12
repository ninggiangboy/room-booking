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
 * One night of one inventory resource: whether it can be sold, how many are left, and on what terms.
 *
 * <p>For a pooled resource this row <em>is</em> the overselling defence. The database refuses any
 * write where {@code held + booked + blocked} would exceed {@link #sellableQuantity}, so a commit
 * that would oversell fails rather than succeeding and being detected later. Callers must lock the
 * date rows in a stable order before reading the counts, or two transactions will each read capacity
 * that the other is about to consume.</p>
 *
 * <p>{@link #stayDate} is a civil date in the property's own time zone, never an instant. The price
 * here is a fast materialized input for search; the authoritative amount a guest owes is composed by
 * pricing into a versioned quote.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("availability_days")
public class AvailabilityDay {

    /** Composite resource-and-date primary key. */
    @Id
    private AvailabilityDayId id;
    /** Whether the host is offering this night at all. */
    private boolean hostSellable;
    /** How many can be sold on this night. */
    private int sellableQuantity;
    /** How many are currently held pending checkout. */
    private int heldQuantity;
    /** How many are confirmed sold. */
    private int bookedQuantity;
    /** How many are withheld by a block. */
    private int blockedQuantity;
    /** Materialized nightly price in minor units; search input, not the authoritative quote. */
    private @Nullable Long nightlyPriceMinor;
    /** ISO 4217 currency of that price; never absent when a price is present. */
    private @Nullable String currency;
    /** Version of the pricing decision this row reflects. */
    private int priceVersion;
    /** Shortest stay permitted when arriving on this date. */
    private @Nullable Short minimumStayOnArrival;
    /** Shortest stay permitted when staying through this date. */
    private @Nullable Short minimumStayThrough;
    /** Longest stay permitted covering this date. */
    private @Nullable Short maximumStay;
    /** Whether a stay may begin on this date. */
    private boolean closedToArrival;
    /** Whether a stay may end on this date. */
    private boolean closedToDeparture;
    /** Whether the night may be occupied at all. */
    private boolean closedToStay;
    /** Version of the restriction decision this row reflects. */
    private int restrictionSetVersion;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;

    /**
     * Returns how many of this night are still sellable.
     *
     * <p>Only meaningful while the row is locked. Read outside a lock it is a snapshot that another
     * transaction may already be consuming, which is why it informs a preflight check rather than
     * authorising a sale — the database's capacity constraint is what actually authorises one.</p>
     *
     * @return remaining sellable quantity, never negative
     */
    public int remainingQuantity() {
        return sellableQuantity - heldQuantity - bookedQuantity - blockedQuantity;
    }

    /**
     * Reports whether this night is offered and unrestricted for occupancy.
     *
     * <p>Says nothing about arrival or departure restrictions, which depend on where the night falls
     * within a particular stay and are evaluated against the whole range.</p>
     *
     * @return {@code true} when the host offers the night and it is not closed to stay
     */
    public boolean isOccupiable() {
        return hostSellable && !closedToStay;
    }

    /**
     * Returns the civil date this row describes.
     *
     * @return the stay date, in the property's own time zone
     */
    public LocalDate stayDate() {
        return id.getStayDate();
    }
}
