package dev.ngb.backend.support.internal.model.remedy;

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


/**
 * A hold taken on a source ceiling while a remedy is being decided.
 *
 * <p>Without it, two agents reading the remaining eligible amount a second apart both see the full
 * figure and both authorize it. The reservation makes the cumulative rule a transactional fact.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("remedy_reservations")
public class RemedyReservation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** The case remedy this row belongs to. */
    private @Nullable UUID caseRemedyId;
    /** Which ceiling scope this row carries. */
    private CeilingScope ceilingScope;
    /** The thing the ceiling belongs to, as its owning domain spells it. */
    private String scopeKey;
    /** ISO 4217 alphabetic code the amounts on this row are denominated in. */
    private String currency;
    /** The ceiling the hold was measured against, in minor units. */
    private long ceilingAmountMinor;
    /** How much is held, in minor units. */
    private long reservedAmountMinor;
    /** How much of the hold was actually used, in minor units. */
    private long consumedAmountMinor;
    /** Where the state stands. */
    private RemedyReservationState state;
    /** UTC instant held. */
    private Instant heldAt;
    /** UTC instant expires. */
    private Instant expiresAt;
    /** UTC instant released. */
    private @Nullable Instant releasedAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable ReservationReleaseReason releaseReason;
    /** The held by account holder this row belongs to. */
    private @Nullable UUID heldByAccountHolderId;
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
