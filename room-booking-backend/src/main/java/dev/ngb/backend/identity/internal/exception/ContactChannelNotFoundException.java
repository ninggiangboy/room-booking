package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.platform.exception.base.NotFoundException;

/**
 * Signals that no live contact channel matches the requested identifier for the calling holder.
 *
 * <p>Deliberately does not distinguish "belongs to someone else" from "does not exist": leaking
 * that another account owns a given channel id would let a caller enumerate other accounts'
 * channels one guess at a time.</p>
 */
public class ContactChannelNotFoundException extends NotFoundException {

    /** Stable API code for an absent or not-owned contact channel. */
    public static final String CODE = "CONTACT_CHANNEL_NOT_FOUND";

    /**
     * Creates a not-found failure naming the requested channel.
     *
     * @param channelId channel identifier that did not resolve
     */
    public ContactChannelNotFoundException(UUID channelId) {
        super(CODE, "no contact channel with that id", Map.of("channelId", channelId));
    }
}
