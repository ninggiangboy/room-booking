/**
 * Small stateless helpers for string normalization, duration validation, hashing, secure token
 * generation, and RFC 6238 time-based one-time passwords that are safe to reuse across application
 * layers.
 *
 * <p>Lives under {@code platform} because these helpers depend on nothing else in the application
 * and every module is free to use them.</p>
 */
@org.springframework.modulith.NamedInterface("util")
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.platform.util;
