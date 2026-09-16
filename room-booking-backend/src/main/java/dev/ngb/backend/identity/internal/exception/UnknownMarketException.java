package dev.ngb.backend.identity.internal.exception;

import java.util.Map;

import dev.ngb.backend.platform.exception.base.BadRequestException;


/**
 * Signals that an operator supplied a market code that names no market currently usable for
 * decisions.
 *
 * <p>Covers an unknown code as well as one whose market exists but is a draft, suspended, or
 * retired — {@code market.MarketLookup} deliberately collapses those into one empty result, since
 * none of them is a market this codebase may resolve a holder into.</p>
 */
public class UnknownMarketException extends BadRequestException {

    /** Stable API code for a market code that is unknown or not currently usable. */
    public static final String CODE = "UNKNOWN_MARKET";

    /**
     * Creates a validation failure naming the rejected code.
     *
     * @param marketCode the code the operator supplied
     */
    public UnknownMarketException(String marketCode) {
        super(
                CODE,
                "marketCode does not name a market that is currently active",
                Map.of("marketCode", marketCode));
    }
}
