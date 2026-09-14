/**
 * Own risk signal, decision, enforcement, challenge, restriction, and content moderation as one module — the platform's general-purpose defense layer, distinct from `review`'s narrower, review-specific moderation workflow and from `support`'s case-driven dispute resolution.
 *
 * <p>This is a closed Spring Modulith application module: everything under {@code internal}
 * is invisible to every other module, and only the types at this root are a legitimate
 * cross-module dependency. See {@code docs/modules/trust.md} for the full aggregate-cluster
 * map, public API, and rationale.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.trust;
