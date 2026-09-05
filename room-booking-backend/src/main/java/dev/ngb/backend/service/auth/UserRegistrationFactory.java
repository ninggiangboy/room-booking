package dev.ngb.backend.service.auth;

import java.time.Clock;
import java.time.Instant;
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
 * Constructs a new user and its initial guest-role row using one timestamp and identifier.
 *
 * <p>{@code @Component} makes the factory injectable. Lombok generates constructor injection for
 * the encoder and clock. Package-private visibility keeps partially constructed registration
 * values inside the authentication package.</p>
 */
@Component
@RequiredArgsConstructor
class UserRegistrationFactory {

    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    NewUser create(
            String email,
            String rawPassword,
            String displayName) {
        Instant now = clock.instant();
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .displayName(displayName)
                .status(UserStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();

        UserRole initialRole = UserRole.builder()
                .id(UserRoleId.builder()
                        .userId(user.getId())
                        .role(Role.GUEST)
                        .build())
                .createdAt(now)
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
