/**
 * Email-delivery port and its Spring Mail SMTP adapter.
 *
 * <p>{@link dev.ngb.backend.platform.internal.service.mail.EmailSender} is the port every module
 * that needs to send mail depends on; {@code SmtpEmailSender} is its only implementation today and
 * is package-private, reachable solely through the port.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.platform.internal.service.mail;
