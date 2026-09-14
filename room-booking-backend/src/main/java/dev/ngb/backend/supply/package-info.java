/**
 * Own what physically or legally exists to be booked — property, accommodation type, physical unit, listing, rate plan — and the geographic catalog that locates it, as one module answering "what is there," distinct from `inventory`'s "what is sellable right now."
 *
 * <p>This is a closed Spring Modulith application module: everything under {@code internal}
 * is invisible to every other module, and only the types at this root are a legitimate
 * cross-module dependency. See {@code docs/modules/supply.md} for the full aggregate-cluster
 * map, public API, and rationale.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.supply;
