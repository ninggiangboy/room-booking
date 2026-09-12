package dev.ngb.backend.model;

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
import org.springframework.data.relational.core.mapping.Table;

/**
 * One dimension of what a modification proposal would change, before and after.
 *
 * <p>A guest asking why a change costs more gets an answer from these rows rather than from a total
 * that moved. A proposal may not claim the same source allocation twice: the second claim would be
 * double-counted into the delta the guest is asked to pay.</p>
 *
 * <p>Deltas are written with their proposal and never revised, so the row carries no optimistic lock.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("booking_modification_deltas")
public class BookingModificationDelta {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Proposal this delta belongs to. */
    private UUID proposalId;
    /** Which aspect of the booking the row describes. */
    private ModificationDeltaDimension dimension;
    /** Position within the dimension. */
    private short sequenceNumber;
    /** The allocation being changed. Claimed at most once per proposal. */
    private @Nullable String sourceAllocationReference;
    /** The allocation it would become. */
    private @Nullable String proposedAllocationReference;
    /** Non-monetary value before the change. */
    private @Nullable String beforeValue;
    /** Non-monetary value after it. */
    private @Nullable String afterValue;
    /** ISO 4217 code, where money is involved. */
    private @Nullable String currency;
    /** Amount before the change. */
    private @Nullable Long beforeAmountMinor;
    /** Amount after it. */
    private @Nullable Long afterAmountMinor;
    /** Money the change adds. */
    private long addedAmountMinor;
    /** Money it removes. */
    private long removedAmountMinor;
    /** Quantity the change adds. */
    private BigDecimal addedQuantity;
    /** Quantity it removes. */
    private BigDecimal removedQuantity;
    /** Rule version that produced the delta. */
    private @Nullable String ruleVersion;
    /** Structured code explaining the change to a reader. */
    private String explanationCode;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
