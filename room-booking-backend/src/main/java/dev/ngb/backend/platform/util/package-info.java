/**
 * Small stateless helpers for string normalization, duration validation, hashing, and secure token
 * generation that are safe to reuse across application layers.
 *
 * <p>Lives under {@code platform} because these helpers depend on nothing else in the application
 * and every module is free to use them.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.platform.util;
