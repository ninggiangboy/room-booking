package dev.ngb.backend.identity.internal.web;

import dev.ngb.backend.identity.internal.model.Role;
import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import dev.ngb.backend.identity.internal.model.account.User;
import dev.ngb.backend.identity.internal.model.account.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;


/**
 * Immutable API projection that deliberately excludes internal fields such as credential material.
 *
 * <p>This record is intentionally separate from {@link User}. Returning persistence entities from
 * controllers could accidentally expose a newly added sensitive field. A record also prevents
 * response state from changing after construction.</p>
 *
 * @param id stable account identifier
 * @param email normalized email address
 * @param phoneNumber optional phone number
 * @param displayName public display name
 * @param avatarUrl optional avatar location
 * @param status account lifecycle status
 * @param emailVerifiedAt verification instant of the primary email channel, or {@code null} when
 *     unverified
 * @param accountHolderId identifier of the person account holder the platform transacts with, or
 *     {@code null} when one has not been resolved
 * @param roles immutable list of authorities granted to the user
 */
public record UserResponse(
        UUID id,
        String email,
        @Nullable String phoneNumber,
        String displayName,
        @Nullable String avatarUrl,
        UserStatus status,
        @Nullable Instant emailVerifiedAt,
        @Nullable UUID accountHolderId,
        List<Role> roles) {

    /**
     * Copies a persistence entity and its separately stored roles into a safe response, without a
     * resolved account holder identifier.
     *
     * @param user persistence entity to project
     * @param emailChannel the user's primary email contact channel, or {@code null} when none has
     *     been created yet
     * @param roles roles loaded from the user-role repository
     * @return immutable user response without internal persistence fields
     */
    public static UserResponse from(
            User user, @Nullable ContactChannel emailChannel, List<Role> roles) {
        return from(user, emailChannel, null, roles);
    }

    /**
     * Copies a persistence entity, its primary email channel, its account holder, and its
     * separately stored roles into a safe response.
     *
     * @param user persistence entity to project
     * @param emailChannel the user's primary email contact channel, or {@code null} when none has
     *     been created yet
     * @param accountHolderId the user's person account holder identifier, or {@code null} when one
     *     has not been resolved
     * @param roles roles loaded from the user-role repository
     * @return immutable user response without internal persistence fields
     */
    public static UserResponse from(
            User user,
            @Nullable ContactChannel emailChannel,
            @Nullable UUID accountHolderId,
            List<Role> roles) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getStatus(),
                emailChannel != null ? emailChannel.getVerifiedAt() : null,
                accountHolderId,
                // Defensively copy the list so callers cannot mutate a response after construction.
                List.copyOf(roles));
    }
}
