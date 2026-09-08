package dev.ngb.backend.service.auth;

import java.time.Clock;
import java.util.UUID;

import dev.ngb.backend.model.Role;
import dev.ngb.backend.model.User;
import dev.ngb.backend.model.UserRole;
import dev.ngb.backend.model.UserRoleId;
import dev.ngb.backend.model.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Constructs a new user and its initial guest-role row using one identifier.
 *
 * <p>{@code @Component} makes the factory injectable. Lombok generates constructor injection for
 * the encoder and the shared clock. Package-private visibility keeps partially constructed
 * registration values inside the authentication package.</p>
 */
@Component
@RequiredArgsConstructor
class UserRegistrationFactory {

    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /**
     * Builds the user and role rows a registration must persist together.
     *
     * <p>The role row is inserted by an explicit statement rather than by an audited repository
     * save, so its grant instant is stamped here from the shared clock. Leaving it unset would make
     * the insert fail, and letting the database supply it would introduce a second clock.</p>
     *
     * @param email normalized login address
     * @param rawPassword password to encode, never stored in its original form
     * @param displayName public name shown to other users
     * @return user aggregate and its initial guest-role assignment
     */
    NewUser create(
            String email,
            String rawPassword,
            String displayName) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .displayName(displayName)
                .status(UserStatus.ACTIVE)
                .build();

        UserRole initialRole = UserRole.builder()
                .id(UserRoleId.builder()
                        .userId(user.getId())
                        .role(Role.GUEST)
                .build())
                .createdAt(clock.instant())
                .build();

        return new NewUser(user, initialRole);
    }

    /**
     * Immutable pair of records that must be persisted together during registration.
     *
     * @param user new user aggregate
     * @param initialRole new user's guest-role assignment
     */
    record NewUser(User user, UserRole initialRole) {
    }
}
