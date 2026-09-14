/**
 * Own the read side of search and recommendation — ranking policy, epoch, exposure, and the listing and guest profiles they score — as a module that is entirely derived and rebuildable, and that reads without ever deciding.
 *
 * <p>This is a closed Spring Modulith application module: everything under {@code internal}
 * is invisible to every other module, and only the types at this root are a legitimate
 * cross-module dependency. See {@code docs/modules/discovery.md} for the full aggregate-cluster
 * map, public API, and rationale.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"review :: types"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.discovery;
