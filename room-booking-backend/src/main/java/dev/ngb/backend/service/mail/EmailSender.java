package dev.ngb.backend.service.mail;

/**
 * Port used by application code to send email without depending on SMTP details.
 *
 * <p>An interface defines behavior without implementation. Authentication depends on this
 * abstraction, while Spring injects the available SMTP adapter.</p>
 */
public interface EmailSender {

    /**
     * Sends a plain-text message to one recipient.
     *
     * @param recipient destination email address
     * @param subject message subject line
     * @param body plain-text message body
     */
    void send(String recipient, String subject, String body);
}
