package dev.ngb.backend.identity.internal.service.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

import dev.ngb.backend.identity.EmailVerificationIssued;
import dev.ngb.backend.identity.PasswordResetIssued;
import dev.ngb.backend.platform.EmailSender;

/**
 * Sends authentication-related token emails after their database transactions commit.
 *
 * <p>{@code @Component} registers this shared notifier, and Lombok's {@code @Slf4j} generates the
 * logger used when SMTP fails. Constructor {@code @Value} parameters inject separate frontend URLs
 * for verification and reset flows. Package-private visibility keeps this adapter internal to the
 * authentication package.</p>
 *
 * <p><b>Deliberately not {@code @ApplicationModuleListener}.</b> That annotation is the default for
 * an event handled across module boundaries, but it routes the event through Spring Modulith's event
 * publication registry, which persists the event's serialized payload in the {@code event_publication}
 * table — indefinitely, under the default {@code completion-mode=update}. Both
 * {@link EmailVerificationIssued} and {@link PasswordResetIssued} carry a raw, unhashed token, and
 * every other token in this application is stored as a digest, never as a raw value. Registering
 * either event with the registry would persist the one raw secret this system otherwise never
 * writes to a table with no retention policy of its own. These two listeners stay on a bare
 * {@link TransactionalEventListener}, which only ever holds the event in memory, until the
 * corresponding tokens can be encrypted at rest (a decision tracked separately, outside this
 * migration). See {@code docs/architecture/event-publication-registry.md}.</p>
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
