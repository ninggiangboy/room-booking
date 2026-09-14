/**
 * Own operator authority, versioned configuration, change control, and the operational command catalogue — the module that makes an administrative action a named domain command with an approver, never a database edit.
 *
 * <p>This is a closed Spring Modulith application module: everything under {@code internal}
 * is invisible to every other module, and only the types at this root are a legitimate
 * cross-module dependency. See {@code docs/modules/admin.md} for the full aggregate-cluster
 * map, public API, and rationale.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.admin;
