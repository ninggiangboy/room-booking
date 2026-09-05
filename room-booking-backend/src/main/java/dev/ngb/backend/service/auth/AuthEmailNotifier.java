package dev.ngb.backend.service.auth;

import dev.ngb.backend.event.EmailVerificationIssued;
import dev.ngb.backend.event.PasswordResetIssued;
import dev.ngb.backend.service.mail.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Sends authentication-related token emails after their database transactions commit.
 *
 * <p>{@code @Component} registers this shared notifier, and Lombok's {@code @Slf4j} generates the
 * logger used when SMTP fails. Constructor {@code @Value} parameters inject separate frontend URLs
 * for verification and reset flows. Package-private visibility keeps this adapter internal to the
 * authentication package.</p>
 */
@Slf4j
@Component
class AuthEmailNotifier {

    private final EmailSender emailSender;
    private final String verificationUrl;
    private final String passwordResetUrl;

    /**
     * Creates the notifier with its delivery port and externally configured frontend URLs.
     *
     * @param emailSender transport-independent mail delivery port
     * @param verificationUrl frontend email-verification page
     * @param passwordResetUrl frontend password-reset page
     */
    AuthEmailNotifier(
            EmailSender emailSender,
            @Value("${app.email-verification.url:http://localhost:3000/verify-email}")
                    String verificationUrl,
            @Value("${app.password-reset.url:http://localhost:3000/reset-password}")
                    String passwordResetUrl) {
        this.emailSender = emailSender;
        this.verificationUrl = verificationUrl;
        this.passwordResetUrl = passwordResetUrl;
    }

    /**
     * Sends the verification link after its token transaction commits.
     *
     * @param event immutable recipient and raw verification token
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void sendVerificationEmail(EmailVerificationIssued event) {
        String link = buildTokenLink(verificationUrl, event.rawToken());
        send(
                event.recipient(),
                "Verify your email address",
                "Verify your email address by opening this link:\n\n" + link,
                "email verification");
    }

    /**
     * Sends the password-reset link after its token transaction commits.
     *
     * @param event immutable recipient and raw password-reset token
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void sendPasswordResetEmail(PasswordResetIssued event) {
        String link = buildTokenLink(passwordResetUrl, event.rawToken());
        send(
                event.recipient(),
                "Reset your password",
                "Reset your password by opening this link:\n\n" + link
                        + "\n\nIf you did not request this, you can ignore this email.",
                "password reset");
    }

    private void send(String recipient, String subject, String body, String messageType) {
        try {
            emailSender.send(recipient, subject, body);
        } catch (MailException exception) {
            // The token transaction is already committed, so delivery failure cannot roll it back.
            log.error(
                    "Could not send {} message to {} after committing its token",
                    messageType,
                    recipient,
                    exception);
        }
    }

    private static String buildTokenLink(String baseUrl, String rawToken) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();
    }
}
