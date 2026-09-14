/**
 * Own the one piece of configuration every other domain must resolve against instead of assuming: which market a fact is governed under, which legal entity is accountable for it, and which exact, effective-dated policy version applied — so that "Vietnam" (or any market added later) is data every module reads, never a constant any module hard-codes.
 *
 * <p>This is a closed Spring Modulith application module: everything under {@code internal}
 * is invisible to every other module, and only the types at this root are a legitimate
 * cross-module dependency. See {@code docs/modules/market.md} for the full aggregate-cluster
 * map, public API, and rationale.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.market;
