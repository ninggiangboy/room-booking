package dev.ngb.backend.service.user;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import dev.ngb.backend.exception.UserAccountDisabledException;
import dev.ngb.backend.exception.UserNotFoundException;
import dev.ngb.backend.model.User;
import dev.ngb.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Loads users by identifier with the application's standard not-found behavior.
 *
 * <p>{@code @Component} makes the shared lookup injectable, while Lombok's
 * {@code @RequiredArgsConstructor} generates constructor injection for the final repository. This
 * removes duplicated lookup/error mapping and active-account checks from account and verification
 * services.</p>
 */
@Component
@RequiredArgsConstructor
public class UserFinder {

    private final UserRepository userRepository;

    /**
     * Finds a user by identifier.
     *
     * @param userId user identifier
     * @return matching user
     * @throws UserNotFoundException when no user has the identifier
     */
    public User findById(UUID userId) {
        return findById(userId, () -> new UserNotFoundException(userId));
    }

    /**
     * Finds a user by identifier, mapping an absent row to the caller's exception.
     *
     * @param userId user identifier
     * @param notFoundException exception supplier for an absent user
     * @return matching user
     */
    public User findById(UUID userId, Supplier<? extends RuntimeException> notFoundException) {
        return userRepository.findById(userId).orElseThrow(notFoundException);
    }

    /**
     * Finds a user by normalized email address, mapping an absent row to the caller's exception.
     *
     * @param email normalized email address
     * @param notFoundException exception supplier for an absent user
     * @return matching user
     */
    public User findByEmail(String email, Supplier<? extends RuntimeException> notFoundException) {
        return userRepository.findByEmail(email).orElseThrow(notFoundException);
    }

    /**
     * Finds an active user by normalized email address when one exists.
     *
     * <p>This variant is suitable for flows that must not reveal whether an email belongs to an
     * account, such as password-reset requests.</p>
     *
     * @param email normalized email address
     * @return active matching user, or empty when the user is absent or inactive
     */
    public Optional<User> findActiveByEmailIfPresent(String email) {
        return userRepository.findByEmail(email).filter(User::isActive);
    }

    /**
     * Finds a user while acquiring a transaction-scoped row lock.
     *
     * <p>The caller must invoke this method within a transaction. Use it only when the enclosing
     * workflow needs to serialize changes for this user.</p>
     *
     * @param userId user identifier
     * @return matching locked user
     * @throws UserNotFoundException when no user has the identifier
     */
    public User findByIdForUpdate(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /**
     * Finds a user that may perform business operations.
     *
     * @param userId user identifier
     * @return matching active user
     * @throws UserNotFoundException when no user has the identifier
     * @throws dev.ngb.backend.exception.UserAccountDisabledException when the account is inactive
     */
    public User findActiveById(UUID userId) {
        return findActiveById(userId, () -> new UserNotFoundException(userId));
    }

    /**
     * Finds an active user, mapping an absent row to the caller's exception.
     *
     * @param userId user identifier
     * @param notFoundException exception supplier for an absent user
     * @return matching active user
     * @throws UserAccountDisabledException when the account is inactive
     */
    public User findActiveById(
            UUID userId, Supplier<? extends RuntimeException> notFoundException) {
        User user = findById(userId, notFoundException);
        requireActive(user);
        return user;
    }

    /**
     * Finds an active user by normalized email address, mapping an absent row to the caller's
     * exception.
     *
     * @param email normalized email address
     * @param notFoundException exception supplier for an absent user
     * @return matching active user
     * @throws UserAccountDisabledException when the account is inactive
     */
    public User findActiveByEmail(
            String email, Supplier<? extends RuntimeException> notFoundException) {
        User user = findByEmail(email, notFoundException);
        requireActive(user);
        return user;
    }

    /**
     * Finds an active user while acquiring a transaction-scoped row lock.
     *
     * @param userId user identifier
     * @return matching locked, active user
     * @throws UserNotFoundException when no user has the identifier
     * @throws dev.ngb.backend.exception.UserAccountDisabledException when the account is inactive
     */
    public User findActiveByIdForUpdate(UUID userId) {
        User user = findByIdForUpdate(userId);
        requireActive(user);
        return user;
    }

    private void requireActive(User user) {
        if (!user.isActive()) {
            throw new UserAccountDisabledException(user);
        }
    }
}
