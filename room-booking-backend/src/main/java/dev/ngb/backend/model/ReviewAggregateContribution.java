package dev.ngb.backend.model;

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
 * Which publication contributed which value to which aggregate.
 *
 * <p>Exists so that an incremental delta can be proved idempotent and a rebuild can be compared
 * against what was actually counted, rather than both being believed.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_aggregate_contributions")
public class ReviewAggregateContribution {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Aggregate this fed. */
    private UUID reviewPublicAggregateId;
    /** Rule version it was counted under. */
    private int aggregateRuleVersion;
    /** Review it came from. */
    private UUID reviewRecordId;
    /** Publication interval that qualified it. */
    private UUID reviewPublicationId;
    /** The rating it contributed. */
    private short contributedValue;
    /** When it started counting. */
    private Instant includedFrom;
    /** When it stopped counting. */
    private @Nullable Instant includedUntil;
    /** Event that applied it. */
    private @Nullable UUID sourceEventId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
