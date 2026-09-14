package dev.ngb.backend.growth.internal.model.program;

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
 * One acquisition or retention programme, and the balance sheet behind it.
 *
 * <p>A programme that hands out value names the legal entity that owes it, the book it is
 * recorded in and the ledger account it is drawn against. Value granted by something that names
 * none of those is a liability the finance close will never find.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("growth_programs")
public class GrowthProgram {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the programme, unique across the platform. */
    private String programKey;
    /** Which kind of growth mechanism this programme is. */
    private GrowthProgramKind programKind;
    /** ISO 3166-1 alpha-2 market the programme runs in; null means every market. */
    private @Nullable String marketCode;
    /** Name shown to whoever administers the programme. */
    private String displayName;
    /** What the programme is for, in the words it was approved under. */
    private String purposeStatement;
    /** Whether the programme hands out money, credit or a discount at all. */
    private boolean grantsValue;
    /** The legal entity that owes the value this programme grants. */
    private @Nullable UUID fundingLegalEntityId;
    /** Book the resulting liability is recorded in. */
    private @Nullable UUID accountingBookId;
    /** Ledger account the granted value is drawn against. */
    private @Nullable UUID liabilityAccountId;
    /** Who bears the cost: the platform, the host, a partner, or a mix. */
    private GrowthFunderType funderType;
    /** Operator answerable for the programme. */
    private @Nullable UUID ownerActorId;
    /** Where the programme stands in its own lifecycle. */
    private GrowthProgramStatus status;
    /** UTC instant the programme began accepting participants. */
    private @Nullable Instant openedAt;
    /** UTC instant the programme stopped accepting anybody new. */
    private @Nullable Instant closedAt;
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
