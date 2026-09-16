package dev.ngb.backend.identity.internal.service.contact;

import dev.ngb.backend.identity.internal.model.account.ContactChannelType;

/**
 * Immutable event carrying a newly issued raw verification code to the post-commit delivery
 * listener.
 *
 * <p>A record fits an event because an event is a value that transports data and must not change
 * after publication. Package-private, unlike {@code identity.EmailVerificationIssued}: publisher
 * ({@link ContactChannelService}) and listener ({@link ContactChannelVerificationNotifier}) are
 * both in this package, so there is no cross-package visibility reason to promote it further, the
 * way the two auth events had to be promoted to the module root to be visible from
 * {@code service.auth.verification}/{@code .passwordreset} to {@code service.auth}.</p>
 *
 * @param channelType kind of channel the code was issued for, so the listener picks the right
 *     transport
 * @param destination value to deliver the code to: a phone number or an email address
 * @param rawCode secret typed back by the holder, never stored in raw form
 */
record ContactChannelVerificationIssued(
        ContactChannelType channelType, String destination, String rawCode) {
}
