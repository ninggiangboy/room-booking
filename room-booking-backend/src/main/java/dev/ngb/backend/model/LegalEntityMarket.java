package dev.ngb.backend.model;

import java.time.Instant;

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
 * The span over which one legal entity is accountable in one market.
 *
 * <p>At most one entity is accountable in a market at any instant, enforced by
 * {@code ex_legal_entity_markets_no_overlap}. Two overlapping rows would make "who is the
 * counterparty to this booking" unanswerable.</p>
 *
 * <p>Replacing an entity adds a new span rather than editing the old one, so bookings the previous
 * entity contracted keep naming it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("legal_entity_markets")
public class LegalEntityMarket {

    /** Composite entity, market, and start-instant primary key. */
    @Id
    private LegalEntityMarketId id;
    /** UTC instant the accountability ends, exclusive; {@code null} while current. */
    private @Nullable Instant effectiveUntil;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
