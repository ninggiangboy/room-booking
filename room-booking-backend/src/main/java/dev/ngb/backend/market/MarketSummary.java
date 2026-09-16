package dev.ngb.backend.market;

/**
 * The minimal, non-sensitive projection of a usable market a dependent module may rely on.
 *
 * @param marketCode ISO 3166-1 alpha-2 code, such as {@code VN}
 * @param displayName operator-facing name of the market
 */
public record MarketSummary(String marketCode, String displayName) {
}
