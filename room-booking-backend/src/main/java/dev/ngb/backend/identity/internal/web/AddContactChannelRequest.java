package dev.ngb.backend.identity.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import dev.ngb.backend.identity.internal.model.account.ContactChannelPurpose;
import dev.ngb.backend.identity.internal.model.account.ContactChannelType;

/**
 * Immutable command to register a new contact channel for the authenticated holder.
 *
 * <p>Validation here only rules out a blank or oversized value. The shape a value must take
 * depends on {@code channelType} (an E.164 phone number, an email address), which Bean Validation
 * cannot express as a per-field rule conditioned on a sibling field without a bespoke class-level
 * validator; {@code ContactChannelService} checks the type-specific shape instead, alongside the
 * normalization and uniqueness policy it already owns.</p>
 *
 * @param channelType kind of channel being added
 * @param purpose what the channel will be used for
 * @param value channel value exactly as the holder entered it; an E.164 phone number for
 *     {@code PHONE}, an address for {@code EMAIL}
 */
public record AddContactChannelRequest(
        @NotNull(message = "channelType must not be null")
        ContactChannelType channelType,
        @NotNull(message = "purpose must not be null")
        ContactChannelPurpose purpose,
        @NotBlank(message = "value must not be blank")
        @Size(max = 320, message = "value must be at most 320 characters")
        String value) {
}
