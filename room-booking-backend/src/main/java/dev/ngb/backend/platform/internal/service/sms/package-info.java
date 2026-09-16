/**
 * SMS-delivery port and its interim adapter.
 *
 * <p>{@link dev.ngb.backend.platform.SmsSender} is the port every module that needs to send a text
 * message depends on; {@code LoggingSmsSender} is its only implementation today and is
 * package-private, reachable solely through the port.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.platform.internal.service.sms;
