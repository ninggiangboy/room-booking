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
 * One ISO 4217 currency a market supports.
 *
 * <p>A market may present prices in several currencies but contracts in exactly one, which is what
 * {@link #isContract} marks and what the {@code uk_market_currencies_one_contract} partial unique
 * index enforces. Conflating the two is how a guest ends up owing an amount in a currency the
 * contract never named.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("market_currencies")
public class MarketCurrency {

    /** Composite market-and-currency primary key. */
    @Id
    private MarketCurrencyId id;
    /** Whether contracts in this market are denominated in this currency. */
    private boolean isContract;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
