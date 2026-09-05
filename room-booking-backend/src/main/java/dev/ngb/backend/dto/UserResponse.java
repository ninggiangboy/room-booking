package dev.ngb.backend.dto;

import dev.ngb.backend.model.Role;
import dev.ngb.backend.model.User;
import dev.ngb.backend.model.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable API projection that deliberately excludes internal fields such as the password hash.
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
 * @param emailVerifiedAt verification instant, or {@code null} when unverified
 * @param roles immutable list of authorities granted to the user
 */
public record UserResponse(
        UUID id,
        String email,
        String phoneNumber,
        String displayName,
        String avatarUrl,
        UserStatus status,
        Instant emailVerifiedAt,
        List<Role> roles) {

    /**
     * Copies a persistence entity and its separately stored roles into a safe response.
     *
     * @param user persistence entity to project
     * @param roles roles loaded from the user-role repository
     * @return immutable user response without internal persistence fields
     */
    public static UserResponse from(User user, List<Role> roles) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getStatus(),
                user.getEmailVerifiedAt(),
                // Defensively copy the list so callers cannot mutate a response after construction.
                List.copyOf(roles));
    }
}
