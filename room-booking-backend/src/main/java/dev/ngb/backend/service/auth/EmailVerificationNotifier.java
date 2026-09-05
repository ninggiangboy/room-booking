package dev.ngb.backend.service.auth;

import dev.ngb.backend.event.EmailVerificationIssued;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import dev.ngb.backend.service.mail.EmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Sends verification email only after the transaction that stored its token commits.
 *
 * <p>{@code @Component} registers the listener. Lombok's {@code @RequiredArgsConstructor} injects
 * the mail port, while {@code @Slf4j} generates the private static {@code log} field used below.
 * Package-private visibility keeps this infrastructure detail internal to authentication.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class EmailVerificationNotifier {

    private final EmailSender emailSender;

    @Value("${app.email-verification.url:http://localhost:3000/verify-email}")
    private String verificationUrl;

    /**
     * Builds the frontend verification link and delivers it through the configured email port.
     *
     * <p>{@code @TransactionalEventListener(AFTER_COMMIT)} means this method runs only after the
     * transaction that saved the token succeeds. Email failure is logged because that transaction
     * can no longer be rolled back.</p>
     *
     * @param event immutable recipient and raw-token event
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void sendVerificationEmail(EmailVerificationIssued event) {
        String link = UriComponentsBuilder.fromUriString(verificationUrl)
                .queryParam("token", event.rawToken())
                .build()
                .encode()
                .toUriString();

        try {
            emailSender.send(
                    event.recipient(),
                    "Verify your email address",
                    "Verify your email address by opening this link:\n\n" + link);
        } catch (MailException exception) {
            // The account transaction is already committed, so report delivery failure for retry/operations.
            log.error(
                    "Could not send email verification message to {} after committing its token",
                    event.recipient(),
                    exception);
        }
    }
}
