/**
 * Secret-sealing port and its AES-GCM adapter.
 *
 * <p>{@link dev.ngb.backend.platform.SecretBox} is the port every module that needs to recover
 * material it must read back — as opposed to material it only ever verifies — depends on;
 * {@code AesGcmSecretBox} is its only implementation today and is package-private, reachable
 * solely through the port.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.platform.internal.service.secret;
