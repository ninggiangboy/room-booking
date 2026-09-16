package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.platform.exception.base.BadRequestException;

/**
 * Signals that a verification code was requested, or a code was submitted, for a channel that is
 * already verified.
 */
public class ContactChannelAlreadyVerifiedException extends BadRequestException {

    /** Stable API code for a redundant verification attempt. */
    public static final String CODE = "CONTACT_CHANNEL_ALREADY_VERIFIED";

    /**
     * Creates a validation failure naming the already-verified channel.
     *
     * @param channelId channel that is already verified
     */
    public ContactChannelAlreadyVerifiedException(UUID channelId) {
        super(CODE, "this contact channel is already verified", Map.of("channelId", channelId));
    }
}
