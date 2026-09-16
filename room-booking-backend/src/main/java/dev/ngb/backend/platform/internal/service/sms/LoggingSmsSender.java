package dev.ngb.backend.platform.internal.service.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import dev.ngb.backend.platform.SmsSender;

/**
 * Interim implementation of the application's SMS-sending port.
 *
 * <p>{@code @Component} registers this implementation as an injectable bean, matching
 * {@code SmtpEmailSender}'s structure. Unlike that class, this adapter has no real carrier gateway
 * behind it: no SMS provider account exists for this codebase yet, so there is nothing to hold real
 * credentials for. It logs the message instead of sending it, which keeps every caller of {@link
 * SmsSender} — {@code identity}'s phone contact-channel verification, and any future step-up or
 * MFA flow — working end to end in development and in tests without a live phone number. Replacing
 * it with a provider-backed sender (Twilio, AWS SNS, or a local telco's HTTP gateway) requires only
 * a new {@code @Component} implementing {@link SmsSender}; nothing that calls the port has to
 * change.</p>
 *
 * <p><b>Do not deploy this to a real user-facing environment without replacing it first</b> — an
 * operator reading application logs would otherwise see every one-time code delivered through this
 * class in plaintext.</p>
 */
@Slf4j
@Component
class LoggingSmsSender implements SmsSender {

    /**
     * Logs the message that would have been sent, at a level a deployed environment can filter on.
     *
     * @param recipient destination phone number in E.164 format
     * @param body message text
     */
    @Override
    public void send(String recipient, String body) {
        log.warn(
                "SMS transport is not configured; logging instead of sending to {}: {}",
                recipient,
                body);
    }
}
