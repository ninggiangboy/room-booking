/**
 * Self-service management of additional contact channels beyond the primary email registration
 * creates: adding a channel, proving control of it, listing a holder's channels, and removing one.
 *
 * <p>{@code ContactChannelService} is {@code public} because {@code internal.web.UserController}
 * calls it directly, the same relationship {@code UserAccountService} has with that controller.
 * {@code ContactChannelFactory}, {@code ContactChannelVerificationIssued}, and
 * {@code ContactChannelVerificationNotifier} all stay package-private: nothing outside this package
 * constructs a channel row directly, and the notifier is reachable only by publishing the event, not
 * by calling it.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.identity.internal.service.contact;
