package dev.ngb.backend.identity.internal.exception;

import java.util.Map;

import dev.ngb.backend.identity.internal.model.account.ContactChannelType;
import dev.ngb.backend.platform.exception.base.BadRequestException;

/**
 * Signals that a submitted contact-channel value does not match the shape its declared type
 * requires — an E.164 phone number, or an email address.
 */
public class InvalidContactChannelValueException extends BadRequestException {

    /** Stable API code for a malformed contact-channel value. */
    public static final String CODE = "INVALID_CONTACT_CHANNEL_VALUE";

    /**
     * Creates a validation failure naming the rejected type.
     *
     * @param channelType the declared type the value did not match
     */
    public InvalidContactChannelValueException(ContactChannelType channelType) {
        super(
                CODE,
                "value does not match the required shape for " + channelType.name(),
                Map.of("channelType", channelType.name()));
    }
}
