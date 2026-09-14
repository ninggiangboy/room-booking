/**
 * Own referrals, stored value, loyalty, campaigns, affiliates, and saved demand — as the module where every incentive names whose money it is, and whose absence costs no other module an invariant.
 *
 * <p>This is a closed Spring Modulith application module: everything under {@code internal}
 * is invisible to every other module, and only the types at this root are a legitimate
 * cross-module dependency. See {@code docs/modules/growth.md} for the full aggregate-cluster
 * map, public API, and rationale.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"messaging :: types", "platform"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.growth;
