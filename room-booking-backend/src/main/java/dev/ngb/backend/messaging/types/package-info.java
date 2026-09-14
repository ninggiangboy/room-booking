/**
 * Enums used by another module that already depends on {@code messaging} in every other respect — the R3
 * shared-type rule in {@code docs/modules/platform.md}: a type stays with its natural owner, exposed
 * through a named interface, when moving it to the shared kernel would only hide a real, existing
 * dependency direction. See {@code docs/modules/messaging.md} for which enum lives here and why.
 */
@org.springframework.modulith.NamedInterface("types")
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.messaging.types;
