/**
 * Identity-specific failures that cross from services to the global exception handler.
 *
 * <p>Each exception extends one of {@link dev.ngb.backend.platform.exception.base}'s abstract types
 * and supplies a stable machine-readable code. {@code config}'s exception handler matches only
 * against those base types, never against a concrete type here, so these stay internal to
 * {@code identity} without breaking error handling for any other module.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.identity.internal.exception;
