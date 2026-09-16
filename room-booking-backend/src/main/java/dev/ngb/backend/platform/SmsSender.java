package dev.ngb.backend.platform;

/**
 * Port used by application code to send a short text message without depending on carrier or
 * gateway details.
 *
 * <p>An interface defines behavior without implementation, matching {@link EmailSender}: a module
 * depends on this abstraction, while Spring injects the available transport. No carrier gateway is
 * wired into this codebase yet — see {@code platform.internal.service.sms.LoggingSmsSender}'s
 * Javadoc for what that means today and what replacing it later requires.</p>
 */
public interface SmsSender {

    /**
     * Sends a short text message to one recipient.
     *
     * @param recipient destination phone number in E.164 format
     * @param body message text; callers keep it short enough for a single SMS segment
     */
    void send(String recipient, String body);
}
