package dev.ngb.backend.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Composite identifier for one entity's accountability in one market over one span.
 *
 * <p>The effective start is part of the key because the same entity can be accountable in the same
 * market across separate, non-overlapping spans.</p>
 */
@Getter
@EqualsAndHashCode
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class LegalEntityMarketId implements Serializable {

    /** Entity side of the compound key. */
    private UUID legalEntityId;
    /** Market side of the compound key. */
    private UUID marketId;
    /** UTC instant the accountability began, distinguishing repeated spans. */
    private Instant effectiveFrom;
}
