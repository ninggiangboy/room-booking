package dev.ngb.backend.market;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import dev.ngb.backend.market.internal.model.market.Market;
import dev.ngb.backend.market.internal.repository.market.MarketRepository;
import dev.ngb.backend.platform.ConfigurationLifecycle;

/**
 * Resolves whether a market code names a market other modules may govern a decision by.
 *
 * <p>{@code @Service} identifies business logic; Lombok's {@code @RequiredArgsConstructor}
 * generates constructor injection for the one repository this lookup needs. This is the module's
 * first live consumer-facing type and its only public root type: {@code market} otherwise has no
 * service layer yet, so this class exists specifically so a dependent module never has to reach
 * into {@code market.internal} — and never has to reimplement "does this code name a market that
 * may actually be used" itself, which is exactly the kind of duplicated check
 * {@code docs/conventions/01-architecture-and-layering.md} asks a shared collaborator to own
 * instead.</p>
 *
 * <p>Deliberately returns a summary projection rather than the {@link Market} aggregate: a
 * dependent module has no legitimate reason to see {@code activationVersion} or the optimistic-lock
 * version, and returning the entity itself would tie a caller to a column this module may still
 * reshape.</p>
 */
@Service
@RequiredArgsConstructor
public class MarketLookup {

    private final MarketRepository marketRepository;

    /**
     * Finds a market by its stable code, only when it may currently be used.
     *
     * <p>Delegates to {@link MarketRepository#findByMarketCodeAndLifecycleState}, which pushes the
     * lifecycle check into the query rather than trusting a caller to check {@link
     * Market#isUsable()} after loading the row.</p>
     *
     * @param marketCode ISO 3166-1 alpha-2 code, such as {@code VN}
     * @return a summary of the market when the code names one in {@link
     *     ConfigurationLifecycle#ACTIVE}; empty for an unknown, draft, suspended, or retired code
     */
    public Optional<MarketSummary> findUsableByCode(String marketCode) {
        return marketRepository
                .findByMarketCodeAndLifecycleState(marketCode, ConfigurationLifecycle.ACTIVE)
                .map(market -> new MarketSummary(market.getMarketCode(), market.getDisplayName()));
    }
}
