package dev.ngb.backend.identity.internal.web;

import java.time.Instant;
import java.util.UUID;

import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import dev.ngb.backend.identity.internal.model.account.ContactChannelPurpose;
import dev.ngb.backend.identity.internal.model.account.ContactChannelType;

/**
 * Public projection of a contact channel, deliberately separate from the persistence entity.
 *
 * @param id channel identifier
 * @param channelType kind of channel
 * @param value the channel's value as originally entered
 * @param purpose what the channel is used for
 * @param primary whether this is the holder's channel of record for its type
 * @param verified whether control of the channel has been proven
 * @param verifiedAt instant control was proven, or {@code null} while unverified
 */
public record ContactChannelResponse(
        UUID id,
        ContactChannelType channelType,
        String value,
        ContactChannelPurpose purpose,
        boolean primary,
        boolean verified,
        Instant verifiedAt) {

    /**
     * Projects a persisted channel into its public response shape.
     *
     * @param channel persisted channel to project
     * @return the response projection
     */
    public static ContactChannelResponse from(ContactChannel channel) {
        return new ContactChannelResponse(
                channel.getId(),
                channel.getChannelType(),
                channel.getOriginalValue() != null
                        ? channel.getOriginalValue()
                        : channel.getNormalizedValue(),
                channel.getPurpose(),
                channel.isPrimary(),
                channel.isVerified(),
                channel.getVerifiedAt());
    }
}
