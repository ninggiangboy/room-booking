/**
 * Own what happened in the building and who was entitled to be there — operational stay, arrival instruction, access entitlement, operational task, and incident — as the module that records physical reality with custody, distinct from `messaging`, which records what the platform said about it.
 *
 * <p>This is a closed Spring Modulith application module: everything under {@code internal}
 * is invisible to every other module, and only the types at this root are a legitimate
 * cross-module dependency. See {@code docs/modules/stay.md} for the full aggregate-cluster
 * map, public API, and rationale.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"booking :: types", "platform"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.stay;
