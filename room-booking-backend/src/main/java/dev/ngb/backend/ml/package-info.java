/**
 * Own the machine-learning platform — feature store, label, model registry, and prediction — as the module that guarantees a model advises inside deterministic constraints and never decides.
 *
 * <p>This is a closed Spring Modulith application module: everything under {@code internal}
 * is invisible to every other module, and only the types at this root are a legitimate
 * cross-module dependency. See {@code docs/modules/ml.md} for the full aggregate-cluster
 * map, public API, and rationale.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"analytics :: types", "platform"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.ml;
