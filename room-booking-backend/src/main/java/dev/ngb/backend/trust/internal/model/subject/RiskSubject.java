package dev.ngb.backend.trust.internal.model.subject;

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
import dev.ngb.backend.market.internal.model.market.Market;

import dev.ngb.backend.market.internal.model.market.Market;


/**
 * A stable typed handle for anything the platform can assess.
 *
 * <p>Without it every risk row would carry its own polymorphic pair and no two tables would spell the
 * same actor the same way. Unique on type and source identifier, so the same listing referenced from
 * a signal and from a restriction is one row.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_subjects")
public class RiskSubject {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** What kind of thing this is. */
    private RiskSubjectType subjectType;
    /** The identifier the owning domain uses; unique together with the type. */
    private String sourceId;
    /** The account holder behind an {@code ACCOUNT} subject; required for that type. */
    private @Nullable UUID accountHolderId;
    /** A short label for reviewer screens; never sensitive detail. */
    private @Nullable String displayReference;
    /** Market this subject belongs to, where one applies. */
    private @Nullable UUID marketId;
    /** When the platform first assessed anything about it. */
    private Instant firstSeenAt;
    /** When it was last assessed; never earlier than the first. */
    private Instant lastSeenAt;
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
