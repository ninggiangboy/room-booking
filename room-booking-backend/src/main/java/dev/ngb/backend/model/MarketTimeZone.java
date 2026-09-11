package dev.ngb.backend.model;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One IANA time zone that properties in a market may sit in.
 *
 * <p>A market can span several zones, so the property's own zone remains authoritative for its stay
 * dates and deadlines. This table bounds which zones are legitimate for that market.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("market_time_zones")
public class MarketTimeZone {

    /** Composite market-and-zone primary key. */
    @Id
    private MarketTimeZoneId id;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
