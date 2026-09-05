package dev.ngb.backend.exception;

import java.util.Map;

import dev.ngb.backend.model.User;

/** Blocks business operations for an account whose status is not active. */
public class UserAccountDisabledException extends DomainException {

    /** Stable API code for operations attempted by a non-active account. */
    public static final String CODE = "USER_ACCOUNT_DISABLED";

    /**
     * Creates a forbidden-operation failure containing safe status context.
     *
     * @param user suspended or logically deleted account
     */
    public UserAccountDisabledException(User user) {
        super(
                CODE,
                "user account is not active",
                Map.of(
                        "userId", user.getId(),
                        "status", user.getStatus().name()));
    }
}
