package dev.ngb.backend.model;

import java.io.Serializable;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Composite identifier for one supported value of a market.
 *
 * <p>{@code @EqualsAndHashCode} gives the key value semantics, and {@link Serializable} lets
 * persistence infrastructure transport the compound key as one value.</p>
 */
@Getter
@EqualsAndHashCode
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MarketLocaleId implements Serializable {

    /** Market side of the compound key. */
    private UUID marketId;
    /** BCP 47 locale side of the compound key. */
    private String locale;
}
