/**
 * Own the case-driven side of dispute resolution — support case, evidence custody, damage claim, remedy, and appeal — as one accepted-large module, because its 46 tables are one continuous life cycle (allegation → finding → decision → money movement) rather than 46 independent facts that could be spread across smaller modules.
 *
 * <p>This is a closed Spring Modulith application module: everything under {@code internal}
 * is invisible to every other module, and only the types at this root are a legitimate
 * cross-module dependency. See {@code docs/modules/support.md} for the full aggregate-cluster
 * map, public API, and rationale.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.support;
