package dev.ngb.backend.identity.internal.exception;

import java.util.Map;

import dev.ngb.backend.identity.internal.model.account.ContactChannelType;
import dev.ngb.backend.platform.exception.base.ConflictException;

/**
 * Signals that the calling holder already has a live channel of this type and value.
 *
 * <p>Unlike {@code EmailAlreadyRegisteredException}, this is not an enumeration risk: the check is
 * scoped to the caller's own channels, so the response reveals nothing about any other account.</p>
 */
public class ContactChannelAlreadyRegisteredException extends ConflictException {

    /** Stable API code for a duplicate channel on the caller's own account. */
    public static final String CODE = "CONTACT_CHANNEL_ALREADY_REGISTERED";

    /**
     * Creates a conflict failure naming the duplicated channel.
     *
     * @param channelType kind of channel that duplicated an existing one
     * @param normalizedValue canonical value the caller already has registered
     */
    public ContactChannelAlreadyRegisteredException(
            ContactChannelType channelType, String normalizedValue) {
        super(
                CODE,
                "you already have a channel of this type and value",
                Map.of("channelType", channelType.name(), "value", normalizedValue));
    }
}
