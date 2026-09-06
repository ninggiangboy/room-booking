/**
 * Domain-specific failures that can cross from services to the HTTP exception handler.
 *
 * <p>Each exception supplies a stable machine-readable code. Keeping exceptions independent of
 * HTTP allows services to express business failures without choosing response status codes.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.exception;
