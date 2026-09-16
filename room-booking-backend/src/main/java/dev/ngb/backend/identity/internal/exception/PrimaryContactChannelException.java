package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.platform.exception.base.BadRequestException;

/**
 * Signals that a request tried to remove a holder's primary channel for its type.
 *
 * <p>Removing a primary channel would leave the holder unreachable for that channel type, or would
 * silently need to promote another channel to primary — a decision this self-service endpoint does
 * not make. A holder must add and verify a replacement before this one can go; changing which
 * channel is primary has no endpoint yet, so a primary channel is simply not removable through this
 * flow today.</p>
 */
public class PrimaryContactChannelException extends BadRequestException {

    /** Stable API code for an attempt to remove a primary contact channel. */
    public static final String CODE = "PRIMARY_CONTACT_CHANNEL_CANNOT_BE_REMOVED";

    /**
     * Creates a validation failure naming the protected channel.
     *
     * @param channelId channel that is currently primary for its type
     */
    public PrimaryContactChannelException(UUID channelId) {
        super(CODE, "the primary channel for its type cannot be removed", Map.of("channelId", channelId));
    }
}
