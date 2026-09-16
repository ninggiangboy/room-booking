package dev.ngb.backend.identity.internal.service.contact;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.ngb.backend.identity.internal.model.account.ContactChannelType;
import dev.ngb.backend.platform.EmailSender;
import dev.ngb.backend.platform.SmsSender;

/**
 * Delivers a contact-channel verification code after its issuing transaction commits.
 *
 * <p>{@code @Component} registers this listener; Lombok's {@code @RequiredArgsConstructor}
 * generates constructor injection for both delivery ports, and {@code @Slf4j} generates the
 * logger used when a transport fails. Package-private, matching every other notifier in this
 * codebase.</p>
 *
 * <p><b>Deliberately not {@code @ApplicationModuleListener}</b>, for the same reason
 * {@code AuthEmailNotifier} gives for {@code EmailVerificationIssued}/{@code PasswordResetIssued}:
 * this event carries a raw, unhashed code, and Spring Modulith's event publication registry would
 * persist that secret in plaintext, indefinitely, in a table with no retention policy of its own.
 * This listener stays on a bare {@link TransactionalEventListener}, which only ever holds the
 * event in memory. See {@code docs/architecture/event-publication-registry.md}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ContactChannelVerificationNotifier {

    private final EmailSender emailSender;
    private final SmsSender smsSender;

    /**
     * Delivers the code to the channel's destination after its token transaction commits.
     *
     * @param event immutable channel type, destination, and raw verification code
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void deliverVerificationCode(ContactChannelVerificationIssued event) {
        String body = "Your verification code is " + event.rawCode()
                + ". It expires shortly; if you did not request it, ignore this message.";
        try {
            if (event.channelType() == ContactChannelType.PHONE) {
                smsSender.send(event.destination(), body);
            } else {
                emailSender.send(event.destination(), "Verify your contact channel", body);
            }
        } catch (MailException exception) {
            // The token transaction is already committed, so delivery failure cannot roll it back.
            log.error(
                    "Could not deliver contact-channel verification code to {} after committing its token",
                    event.destination(),
                    exception);
        }
    }
}
