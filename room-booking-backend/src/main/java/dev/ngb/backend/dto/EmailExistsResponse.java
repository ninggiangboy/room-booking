package dev.ngb.backend.dto;

/**
 * Immutable response that reports whether an email address is already registered.
 *
 * @param exists {@code true} when a matching normalized address exists
 */
public record EmailExistsResponse(boolean exists) {
}
