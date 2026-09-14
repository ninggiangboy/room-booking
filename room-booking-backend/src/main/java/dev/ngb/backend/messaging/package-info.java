/**
 * Own conversation, message, notification intent, and delivery as one module — the platform's outbound and inbound communication layer — kept separate from `stay`'s operational workflow even though a feature document groups the two, because the FK graph only supports the coupling running one way.
 *
 * <p>This is a closed Spring Modulith application module: everything under {@code internal}
 * is invisible to every other module, and only the types at this root are a legitimate
 * cross-module dependency. See {@code docs/modules/messaging.md} for the full aggregate-cluster
 * map, public API, and rationale.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.messaging;
