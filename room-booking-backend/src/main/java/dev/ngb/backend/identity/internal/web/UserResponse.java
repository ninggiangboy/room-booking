package dev.ngb.backend.identity.internal.web;

import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;


/**
 * Immutable API projection that deliberately excludes internal fields such as credential material.
 *
 * <p>This record is intentionally separate from {@link AccountHolder}. Returning persistence
 * entities from controllers could accidentally expose a newly added sensitive field. A record also
 * prevents response state from changing after construction.</p>
 *
 * <p>{@code id} is the account holder identifier, which is also what a JWT subject claim names
 * since migration {@code 037} retired the legacy {@code users} table. {@code roleNames} and
 * {@code capabilities} replace the old {@code roles} field: a role name is a convenience label a
 * grant carries, and {@code capabilities} is what authorization decisions actually key on.</p>
 *
 * @param id stable account holder identifier
 * @param email normalized primary email address, or {@code null} when no email channel has been
 *     created yet
 * @param phoneNumber optional phone number
 * @param displayName public display name
 * @param avatarUrl optional avatar location
 * @param status account lifecycle status
 * @param emailVerifiedAt verification instant of the primary email channel, or {@code null} when
 *     unverified
 * @param roleNames convenience labels of the account's currently effective global grants
 * @param capabilities names of every capability currently in force globally for the account
 */
public record UserResponse(
        UUID id,
        @Nullable String email,
        @Nullable String phoneNumber,
        String displayName,
        @Nullable String avatarUrl,
        AccountHolderStatus status,
        @Nullable Instant emailVerifiedAt,
        List<String> roleNames,
        List<String> capabilities) {

    /**
     * Copies a persistence entity, its contact channels, and its currently effective grants into a
     * safe response.
     *
     * @param holder persistence entity to project
     * @param emailChannel the holder's primary email contact channel, or {@code null} when none has
     *     been created yet
     * @param phoneChannel the holder's primary phone contact channel, or {@code null} when none
     *     has been created
     * @param roleNames role labels of the holder's currently effective global grants
     * @param capabilities capability names currently in force globally for the holder
     * @return immutable user response without internal persistence fields
     */
    public static UserResponse from(
            AccountHolder holder,
            @Nullable ContactChannel emailChannel,
            @Nullable ContactChannel phoneChannel,
            List<String> roleNames,
            Set<String> capabilities) {
        return new UserResponse(
                holder.getId(),
                emailChannel != null ? emailChannel.getNormalizedValue() : null,
                phoneChannel != null ? phoneChannel.getNormalizedValue() : null,
                holder.getDisplayName(),
                holder.getAvatarUrl(),
                holder.getStatus(),
                emailChannel != null ? emailChannel.getVerifiedAt() : null,
                // Defensively copy so callers cannot mutate a response after construction.
                List.copyOf(roleNames),
                capabilities.stream().sorted().toList());
    }
}
