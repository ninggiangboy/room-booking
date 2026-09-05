package dev.ngb.backend.service.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP-backed implementation of the application's email-sending port.
 *
 * <p>{@code @Component} registers this implementation as an injectable bean. Lombok's
 * {@code @RequiredArgsConstructor} generates constructor injection for the final
 * {@link JavaMailSender}. Package-private visibility hides the adapter from other packages, which
 * should depend on {@link EmailSender} instead.</p>
 */
@Component
@RequiredArgsConstructor
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    /**
     * Builds a simple plain-text message and delegates transport to Spring Mail.
     *
     * <p>{@code @Override} lets the compiler verify that this method fulfills the port contract.</p>
     *
     * @param recipient destination email address
     * @param subject message subject line
     * @param body plain-text message body
     */
    @Override
    public void send(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
