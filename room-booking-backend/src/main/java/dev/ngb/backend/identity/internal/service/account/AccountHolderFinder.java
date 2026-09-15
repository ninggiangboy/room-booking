package dev.ngb.backend.identity.internal.service.account;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import dev.ngb.backend.identity.internal.model.account.ContactChannelType;
import dev.ngb.backend.identity.internal.repository.account.AccountHolderRepository;
import dev.ngb.backend.identity.internal.repository.account.ContactChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import dev.ngb.backend.identity.internal.exception.UserAccountDisabledException;
import dev.ngb.backend.identity.internal.exception.UserNotFoundException;


/**
 * Loads account holders by identifier or email with the application's standard not-found
 * behavior.
 *
 * <p>{@code @Component} makes the shared lookup injectable, while Lombok's
 * {@code @RequiredArgsConstructor} generates constructor injection for the two final repositories.
 * This removes duplicated lookup/error mapping and active-account checks from account and
 * verification services. Replaces {@code service.user.UserFinder} now that {@link AccountHolder}
 * is the principal root: an email lookup can no longer read a unique {@code users.email} column,
 * since {@code contact_channels} deliberately allows more than one account to claim the same
 * address before either proves it.</p>
 */
@Component
@RequiredArgsConstructor
public class AccountHolderFinder {

    private final AccountHolderRepository accountHolderRepository;
    private final ContactChannelRepository contactChannelRepository;

    /**
     * Finds a holder by identifier.
     *
     * @param holderId holder identifier
     * @return matching holder
     * @throws UserNotFoundException when no holder has the identifier
     */
    public AccountHolder findById(UUID holderId) {
        return findById(holderId, () -> new UserNotFoundException(holderId));
    }

    /**
     * Finds a holder by identifier, mapping an absent row to the caller's exception.
     *
     * @param holderId holder identifier
     * @param notFoundException exception supplier for an absent holder
     * @return matching holder
     */
    public AccountHolder findById(UUID holderId, Supplier<? extends RuntimeException> notFoundException) {
        return accountHolderRepository.findById(holderId).orElseThrow(notFoundException);
    }

    /**
     * Finds a holder by normalized email address, mapping an absent row to the caller's exception.
     *
     * <p>Resolves the holder through its current primary email channel rather than a unique
     * column: when more than one unverified claim exists for the address, the oldest claim wins,
     * since it was made first.</p>
     *
     * @param email normalized email address
     * @param notFoundException exception supplier for an absent holder
     * @return matching holder
     */
    public AccountHolder findByEmail(String email, Supplier<? extends RuntimeException> notFoundException) {
        ContactChannel channel = contactChannelRepository
                .findAllByChannelTypeAndNormalizedValueAndIsPrimaryTrue(ContactChannelType.EMAIL, email)
                .stream()
                .min(Comparator.comparing(ContactChannel::getCreatedAt))
                .orElseThrow(notFoundException);
        return findById(channel.getAccountHolderId(), notFoundException);
    }

    /**
     * Finds an active holder by normalized email address when one exists.
     *
     * <p>This variant is suitable for flows that must not reveal whether an email belongs to an
     * account, such as password-reset requests.</p>
     *
     * @param email normalized email address
     * @return active matching holder, or empty when the holder is absent or inactive
     */
    public Optional<AccountHolder> findActiveByEmailIfPresent(String email) {
        return contactChannelRepository
                .findAllByChannelTypeAndNormalizedValueAndIsPrimaryTrue(ContactChannelType.EMAIL, email)
                .stream()
                .min(Comparator.comparing(ContactChannel::getCreatedAt))
                .flatMap(channel -> accountHolderRepository.findById(channel.getAccountHolderId()))
                .filter(AccountHolderFinder::isActive);
    }

    /**
     * Finds a holder while acquiring a transaction-scoped row lock.
     *
     * <p>The caller must invoke this method within a transaction. Use it only when the enclosing
     * workflow needs to serialize changes for this holder.</p>
     *
     * @param holderId holder identifier
     * @return matching locked holder
     * @throws UserNotFoundException when no holder has the identifier
     */
    public AccountHolder findByIdForUpdate(UUID holderId) {
        return accountHolderRepository.findByIdForUpdate(holderId)
                .orElseThrow(() -> new UserNotFoundException(holderId));
    }

    /**
     * Finds a holder that may perform business operations.
     *
     * @param holderId holder identifier
     * @return matching active holder
     * @throws UserNotFoundException when no holder has the identifier
     * @throws UserAccountDisabledException when the account is inactive
     */
    public AccountHolder findActiveById(UUID holderId) {
        return findActiveById(holderId, () -> new UserNotFoundException(holderId));
    }

    /**
     * Finds an active holder, mapping an absent row to the caller's exception.
     *
     * @param holderId holder identifier
     * @param notFoundException exception supplier for an absent holder
     * @return matching active holder
     * @throws UserAccountDisabledException when the account is inactive
     */
    public AccountHolder findActiveById(
            UUID holderId, Supplier<? extends RuntimeException> notFoundException) {
        AccountHolder holder = findById(holderId, notFoundException);
        requireActive(holder);
        return holder;
    }

    /**
     * Finds an active holder by normalized email address, mapping an absent row to the caller's
     * exception.
     *
     * @param email normalized email address
     * @param notFoundException exception supplier for an absent holder
     * @return matching active holder
     * @throws UserAccountDisabledException when the account is inactive
     */
    public AccountHolder findActiveByEmail(
            String email, Supplier<? extends RuntimeException> notFoundException) {
        AccountHolder holder = findByEmail(email, notFoundException);
        requireActive(holder);
        return holder;
    }

    /**
     * Finds an active holder while acquiring a transaction-scoped row lock.
     *
     * @param holderId holder identifier
     * @return matching locked, active holder
     * @throws UserNotFoundException when no holder has the identifier
     * @throws UserAccountDisabledException when the account is inactive
     */
    public AccountHolder findActiveByIdForUpdate(UUID holderId) {
        AccountHolder holder = findByIdForUpdate(holderId);
        requireActive(holder);
        return holder;
    }

    private static boolean isActive(AccountHolder holder) {
        return holder.getStatus() == AccountHolderStatus.ACTIVE;
    }

    private void requireActive(AccountHolder holder) {
        if (!isActive(holder)) {
            throw new UserAccountDisabledException(holder);
        }
    }
}
