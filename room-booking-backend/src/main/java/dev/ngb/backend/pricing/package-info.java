/**
 * Own how a nightly rate becomes a quoted, taxed price — rules, promotions, quotes, and tax — as one module `booking` reads from, never one it reasons about internally.
 *
 * <p>This is a closed Spring Modulith application module: everything under {@code internal}
 * is invisible to every other module, and only the types at this root are a legitimate
 * cross-module dependency. See {@code docs/modules/pricing.md} for the full aggregate-cluster
 * map, public API, and rationale.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.pricing;
