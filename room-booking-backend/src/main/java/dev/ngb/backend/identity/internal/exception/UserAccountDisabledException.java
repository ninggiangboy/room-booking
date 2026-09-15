package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;

import dev.ngb.backend.platform.exception.base.ForbiddenException;


/**
 * Blocks business operations for an account holder whose status is not active.
 *
 * <p>The type name and stable {@link #CODE} predate migration {@code 037}'s retirement of the
 * legacy {@code users} table and stay unchanged: they are the public API contract, and every
 * client that already handles {@code USER_ACCOUNT_DISABLED} should keep working without a
 * change.</p>
 */
public class UserAccountDisabledException extends ForbiddenException {

    /** Stable API code for operations attempted by a non-active account. */
    public static final String CODE = "USER_ACCOUNT_DISABLED";

    /**
     * Creates a forbidden-operation failure containing safe status context.
     *
     * @param holder suspended or closed account holder
     */
    public UserAccountDisabledException(AccountHolder holder) {
        super(
                CODE,
                "user account is not active",
                Map.of(
                        "userId", holder.getId(),
                        "status", holder.getStatus().name()));
    }
}
