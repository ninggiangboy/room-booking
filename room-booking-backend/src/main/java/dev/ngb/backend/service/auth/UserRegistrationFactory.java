package dev.ngb.backend.service.auth;

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
 * the encoder. Package-private visibility keeps partially constructed registration values inside
 * the authentication package.</p>
 */
@Component
@RequiredArgsConstructor
class UserRegistrationFactory {

    private final PasswordEncoder passwordEncoder;

    /**
     * Builds the user and role rows a registration must persist together.
     *
     * <p>The role's grant instant is left unset here on purpose: the role row is inserted by an
     * explicit statement rather than an audited save, so it cannot pick up the same instant Spring
     * Data JDBC auditing stamps on the user. The caller must persist the user first and reuse its
     * {@code createdAt} as the role's grant instant, so both rows share the registration command's
     * single decision instant instead of two separate clock reads.</p>
     *
     * @param email normalized login address
     * @param rawPassword password to encode, never stored in its original form
     * @param displayName public name shown to other users
     * @return user aggregate and its initial guest-role assignment, grant instant still unset
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
