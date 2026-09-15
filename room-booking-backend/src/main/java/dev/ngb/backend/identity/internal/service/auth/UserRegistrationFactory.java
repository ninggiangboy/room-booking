package dev.ngb.backend.identity.internal.service.auth;

import java.time.Instant;
import java.util.UUID;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.identity.internal.model.account.AccountHolderType;
import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import dev.ngb.backend.identity.internal.model.account.ContactChannelPurpose;
import dev.ngb.backend.identity.internal.model.account.ContactChannelType;
import dev.ngb.backend.identity.internal.model.account.MarketContextState;
import dev.ngb.backend.identity.internal.model.credential.AuthCredential;
import dev.ngb.backend.identity.internal.model.credential.CredentialType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;


/**
 * Constructs the rows every registration must create together: the person account holder the
 * platform transacts with, the primary email contact channel, and the password credential that
 * authenticates future logins.
 *
 * <p>{@code @Component} makes the factory injectable. Lombok generates constructor injection for
 * the encoder. Package-private visibility keeps partially constructed registration values inside
 * the authentication package.</p>
 *
 * <p>Since migration {@code 037} retired the legacy {@code users} table, {@link AccountHolder} is
 * the only aggregate a registration creates for the principal itself; there is no separate user
 * row and no {@code user_roles} assignment. {@link
 * dev.ngb.backend.identity.internal.service.authz.CapabilityGrantService} is the sole writer of
 * {@code capability_grants}, so {@link AuthenticationService} issues the initial guest grant
 * itself once the account holder built here has been persisted and has an identifier to grant
 * against.</p>
 */
@Component
@RequiredArgsConstructor
class UserRegistrationFactory {

    private final PasswordEncoder passwordEncoder;

    /**
     * Builds every row a registration must persist together.
     *
     * <p>The account holder is created with an unresolved market context: no market-resolution
     * signal is available at registration time, and a guest does not need to transact
     * immediately, so leaving it unresolved is honest rather than guessing a market that was
     * never approved.</p>
     *
     * @param rawEmail email exactly as the caller submitted it
     * @param normalizedEmail case-normalized login address
     * @param rawPassword password to encode, never stored in its original form
     * @param displayName public name shown to other users
     * @param issuedAt registration command's single decision instant, used for rows whose
     *     creation time cannot be recovered from audited fields after the fact
     * @return every row the registration must persist together
     */
    NewAccount create(
            String rawEmail,
            String normalizedEmail,
            String rawPassword,
            String displayName,
            Instant issuedAt) {
        AccountHolder accountHolder = AccountHolder.builder()
                .id(UUID.randomUUID())
                .holderType(AccountHolderType.PERSON)
                .displayName(displayName)
                .status(AccountHolderStatus.ACTIVE)
                .marketCode(null)
                .contextState(MarketContextState.UNRESOLVED)
                .build();

        ContactChannel emailChannel = ContactChannel.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolder.getId())
                .channelType(ContactChannelType.EMAIL)
                .normalizedValue(normalizedEmail)
                .originalValue(rawEmail)
                .purpose(ContactChannelPurpose.ACCOUNT)
                .isPrimary(true)
                .build();

        AuthCredential passwordCredential = AuthCredential.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolder.getId())
                .credentialType(CredentialType.PASSWORD)
                .encoderId("bcrypt")
                .verifierDigest(passwordEncoder.encode(rawPassword))
                .enrolledAt(issuedAt)
                .build();

        return new NewAccount(accountHolder, emailChannel, passwordCredential);
    }

    /**
     * Every row registration must persist together.
     *
     * @param accountHolder new person account holder
     * @param emailChannel new unverified primary email channel
     * @param passwordCredential new password credential
     */
    record NewAccount(
            AccountHolder accountHolder,
            ContactChannel emailChannel,
            AuthCredential passwordCredential) {
    }
}
