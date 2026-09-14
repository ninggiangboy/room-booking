package dev.ngb.backend.discovery.internal.model.event;

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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import dev.ngb.backend.discovery.internal.model.PreferenceDimensionKind;


/**
 * What this session appears to be looking for, which is not what this guest prefers.
 *
 * <p>Keyed on the session pseudonym, always carrying an expiry. Promoting it into the durable profile
 * is a flag somebody sets deliberately: an anonymous session cannot grow into a stored profile by
 * accident, and a guest who declined profiling cannot be promoted at all.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("guest_session_intents")
public class GuestSessionIntent {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Analytical pseudonym for the session this intent belongs to. */
    private String sessionPseudonym;
    /** The account holder this row belongs to. */
    private @Nullable UUID accountHolderId;
    /** The kind of thing this intent is about. */
    private PreferenceDimensionKind dimensionKind;
    /** Which dimension within that kind. */
    private String dimensionKey;
    /** The value observed within this session. */
    private @Nullable BigDecimal observedValue;
    /** How many times it was observed; one passive signal is weak evidence. */
    private int observationCount;
    /** UTC instant it was first observed in this session. */
    private Instant firstObservedAt;
    /** UTC instant it was last observed. */
    private Instant lastObservedAt;
    /** UTC instant the intent expires; session intent is never kept indefinitely. */
    private Instant expiresAt;
    /** Whether this may be folded into the durable profile, which requires a named guest who permitted it. */
    private boolean durablePromotionAllowed;
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
