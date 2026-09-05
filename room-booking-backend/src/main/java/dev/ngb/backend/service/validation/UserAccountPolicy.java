package dev.ngb.backend.service.validation;

import java.util.Objects;

import dev.ngb.backend.exception.UserAccountDisabledException;
import dev.ngb.backend.model.User;
import org.springframework.stereotype.Component;

/**
 * Enforces user-account state rules shared by account and authentication workflows.
 *
 * <p>{@code @Component} registers one stateless policy bean. Centralizing the rule keeps login,
 * refresh, email verification, and account changes consistent.</p>
 */
@Component
public class UserAccountPolicy {

    /**
     * Requires an account to be active before continuing a protected workflow.
     *
     * @param user account to inspect
     * @throws UserAccountDisabledException when the account is suspended or deleted
     */
    public void requireActive(User user) {
        Objects.requireNonNull(user, "user must not be null");
        if (!user.isActive()) {
            throw new UserAccountDisabledException(user);
        }
    }
}
