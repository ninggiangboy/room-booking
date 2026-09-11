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
 * One BCP 47 locale a market supports for presentation.
 *
 * <p>A locale is a rendering preference, never legal authority: changing it changes the language a
 * disclosure is shown in, not which market's rules apply.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("market_locales")
public class MarketLocale {

    /** Composite market-and-locale primary key. */
    @Id
    private MarketLocaleId id;
    /** Whether this is the locale used when the reader expresses no preference. */
    private boolean isDefault;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
