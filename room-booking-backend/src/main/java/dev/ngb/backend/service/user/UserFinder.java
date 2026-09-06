package dev.ngb.backend.service.user;

import java.util.UUID;

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
 * removes duplicated lookup/error mapping from account and verification services.</p>
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
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
