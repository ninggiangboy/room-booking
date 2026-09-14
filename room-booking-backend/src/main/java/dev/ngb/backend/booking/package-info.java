/**
 * Own the stay contract and its full lifecycle — the reservation itself, its revision chain, cancellation, modification, refund instruction, and relocation — as one module, because the revision chain *is* booking state, not an adjunct to it.
 *
 * <p>This is a closed Spring Modulith application module: everything under {@code internal}
 * is invisible to every other module, and only the types at this root are a legitimate
 * cross-module dependency. See {@code docs/modules/booking.md} for the full aggregate-cluster
 * map, public API, and rationale.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform", "pricing :: types"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.booking;
