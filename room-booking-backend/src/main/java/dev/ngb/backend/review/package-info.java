/**
 * Own the right to review a stay, the review's own revision and publication lifecycle, and the aspect and reputation intelligence built on top of it, as one module distinct from the general-purpose `trust` content-moderation pipeline it happens to sit next to in the schema.
 *
 * <p>This is a closed Spring Modulith application module: everything under {@code internal}
 * is invisible to every other module, and only the types at this root are a legitimate
 * cross-module dependency. See {@code docs/modules/review.md} for the full aggregate-cluster
 * map, public API, and rationale.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.review;
