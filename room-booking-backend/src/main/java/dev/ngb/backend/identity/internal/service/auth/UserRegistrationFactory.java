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
import dev.ngb.backend.identity.internal.model.account.User;
import dev.ngb.backend.identity.internal.model.account.UserStatus;
import dev.ngb.backend.identity.internal.model.credential.AuthCredential;
import dev.ngb.backend.identity.internal.model.credential.CredentialType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;


/**
 * Constructs a new user and the rows every registration must create alongside it: the person
 * account holder the platform transacts with, the primary email contact channel, and the password
 * credential that authenticates future logins.
 *
 * <p>{@code @Component} makes the factory injectable. Lombok generates constructor injection for
 * the encoder. Package-private visibility keeps partially constructed registration values inside
 * the authentication package.</p>
 *
 * <p>The initial guest-role grant is no longer built here: {@link
 * dev.ngb.backend.identity.internal.service.authz.CapabilityGrantService} is the sole writer of
 * {@code capability_grants}, so {@link AuthenticationService} issues that grant itself once the
 * account holder it is keyed on has been persisted and has an identifier to grant against.</p>
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
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(normalizedEmail)
                .displayName(displayName)
                .status(UserStatus.ACTIVE)
                .build();

        AccountHolder accountHolder = AccountHolder.builder()
                .id(UUID.randomUUID())
                .holderType(AccountHolderType.PERSON)
                .userId(user.getId())
                .displayName(displayName)
                .status(AccountHolderStatus.ACTIVE)
                .marketCode(null)
                .contextState(MarketContextState.LEGACY_UNRECONCILED)
                .build();

        ContactChannel emailChannel = ContactChannel.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .channelType(ContactChannelType.EMAIL)
                .normalizedValue(normalizedEmail)
                .originalValue(rawEmail)
                .purpose(ContactChannelPurpose.ACCOUNT)
                .isPrimary(true)
                .build();

        AuthCredential passwordCredential = AuthCredential.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .credentialType(CredentialType.PASSWORD)
                .encoderId("bcrypt")
                .verifierDigest(passwordEncoder.encode(rawPassword))
                .enrolledAt(issuedAt)
                .build();

        return new NewAccount(user, accountHolder, emailChannel, passwordCredential);
    }

    /**
     * Every row registration must persist together.
     *
     * @param user new user aggregate
     * @param accountHolder new user's person account holder
     * @param emailChannel new user's unverified primary email channel
     * @param passwordCredential new user's password credential
     */
    record NewAccount(
            User user,
            AccountHolder accountHolder,
            ContactChannel emailChannel,
            AuthCredential passwordCredential) {
    }
}
