package dev.ngb.backend.model;

import java.io.Serializable;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Composite identifier for a user-role assignment.
 *
 * <p>{@code @EqualsAndHashCode} is essential for identifier value semantics. Lombok also generates
 * getters, a builder, and constructors. {@link Serializable} allows persistence infrastructure to
 * transport the compound key as one value.</p>
 */
@Getter
@EqualsAndHashCode
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserRoleId implements Serializable {

    /** User side of the compound key. */
    private UUID userId;
    /** Role side of the compound key. */
    private Role role;
}
